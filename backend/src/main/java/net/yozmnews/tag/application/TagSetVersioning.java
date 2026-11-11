package net.yozmnews.tag.application;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.TagSetActivationResult;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetProblemView;
import net.yozmnews.tag.TagSetReviewResult;
import net.yozmnews.tag.TagSetVersionView;
import net.yozmnews.tag.domain.queue.EvaluationQueueRepository;
import net.yozmnews.tag.domain.tagset.TagSet;
import net.yozmnews.tag.domain.tagset.TagSetDefinition;
import net.yozmnews.tag.domain.tagset.TagSetParse;
import net.yozmnews.tag.domain.tagset.TagSetParser;
import net.yozmnews.tag.domain.tagset.TagSetProblem;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.tagset.TagSetRevision;
import net.yozmnews.tag.domain.tagset.TagSetRevision.Revision;
import net.yozmnews.tag.domain.tagset.TagSetWriter;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 태그 체계 버전 연산 — 버전 목록 · 원문 내보내기 · 확인 · 활성화 — docs/specs/tag.md §4 · tag-R16 ·
 * tag-R22 ~ tag-R25 · tag-R27.
 * <p>
 * {@link TagSetAdministrationService} 가 맡긴다. 확인은 트랜잭션을 열지 않는다. 활성화는 재평가 대상 기사를 트랜잭션 밖에서 읽은 뒤, 한
 * 트랜잭션({@link TransactionTemplate})에서 표 잠금 → 대조 → 쓰기다. 시각은 {@link InstantSource} 에서 얻는다. 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Component
class TagSetVersioning {

	private static final Logger logger = LoggerFactory.getLogger(TagSetVersioning.class);

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final TagSetVersionStore versionStore;

	private final EvaluationQueueRepository queue;

	private final ArticleDirectory articles;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	private final TagSetParser parser = new TagSetParser();

	@Autowired
	TagSetVersioning(TagSetRepository tagSets, ThresholdRepository thresholds, EvaluationQueueRepository queue,
			ArticleDirectory articles, TransactionTemplate transaction) {
		this(tagSets, thresholds, queue, articles, transaction, InstantSource.system());
	}

	TagSetVersioning(TagSetRepository tagSets, ThresholdRepository thresholds, EvaluationQueueRepository queue,
			ArticleDirectory articles, TransactionTemplate transaction, InstantSource clock) {
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.versionStore = new TagSetVersionStore(tagSets, thresholds);
		this.queue = queue;
		this.articles = articles;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** 버전 목록 — 번호가 큰 것부터 */
	List<TagSetVersionView> versions() {
		return this.tagSets.findAllByOrderByNumberDesc().stream().map(TagSetVersioning::view).toList();
	}

	/** 버전의 원문 — 그 버전의 내용과 지금 태그 기준 · 저장 기준. 없는 버전이면 빈 값 */
	Optional<String> versionText(TagSetId id) {
		return this.tagSets.findById(id.value())
			.map(tagSet -> TagSetWriter.write(tagSet.content(), this.thresholds.current(tagSet.id())));
	}

	/** 확인 — 아무것도 쓰지 않는다 */
	TagSetReviewResult review(String text, int recentDays) {
		Objects.requireNonNull(text, "text");
		TagSetDefinition definition;
		switch (draft(text, recentDays)) {
			case Draft.DaysOutOfRange outOfRange -> {
				return new TagSetReviewResult.DaysOutOfRange();
			}
			case Draft.Invalid invalid -> {
				return new TagSetReviewResult.Invalid(invalid.problems());
			}
			case Draft.Ready ready -> definition = ready.definition();
		}
		Instant now = this.clock.instant();
		TagSet base = TagSetDirectoryService.active(this.tagSets);
		return switch (revise(base, definition)) {
			case Revision.Rejected rejected -> new TagSetReviewResult.Invalid(views(rejected.problems()));
			case Revision.Unchanged unchanged -> new TagSetReviewResult.Unchanged();
			case Revision.Changed changed -> new TagSetReviewResult.Ready(changed.changes(),
					recent(recentDays, now).size());
		};
	}

	/** 활성화 — 대조를 통과하면 기준 버전을 끄고 새 버전을 활성으로 넣고, 처음 태그 기준 · 저장 기준과 재평가 대기를 쓴다 */
	TagSetActivationResult activate(String text, int recentDays, ChangeActor actor) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(actor, "actor");
		TagSetDefinition definition;
		switch (draft(text, recentDays)) {
			case Draft.DaysOutOfRange outOfRange -> {
				return new TagSetActivationResult.DaysOutOfRange();
			}
			case Draft.Invalid invalid -> {
				return new TagSetActivationResult.Invalid(invalid.problems());
			}
			case Draft.Ready ready -> definition = ready.definition();
		}
		Instant now = this.clock.instant();
		// 기사 조회는 트랜잭션 밖이다 — 그 사이 처음 본 기사는 ArticleStored 로, 지운 기사는 회차가 대기에서 뺀다(tag-R25)
		List<ArticleId> recent = recent(recentDays, now);
		TagSetActivationResult result = Objects.requireNonNull(this.transaction.execute(status -> {
			this.tagSets.lockForChange();
			TagSet base = TagSetDirectoryService.active(this.tagSets);
			return switch (revise(base, definition)) {
				case Revision.Rejected rejected -> new TagSetActivationResult.Invalid(views(rejected.problems()));
				case Revision.Unchanged unchanged -> new TagSetActivationResult.Unchanged();
				case Revision.Changed changed -> {
					TagSet saved = this.versionStore.next(base, definition, actor, now);
					int requeued = this.queue.requeue(recent, now);
					yield new TagSetActivationResult.Activated(view(saved), changed.changes(), requeued);
				}
			};
		}));
		if (result instanceof TagSetActivationResult.Activated activated) {
			logger.info("태그 체계 버전 {}를 활성으로 바꿨다 — 재평가 대기 {}건", activated.version().number(),
					activated.requeued());
		}
		return result;
	}

	/** 확인 · 활성화의 원문 앞 검사 결과 */
	private sealed interface Draft {

		/** 원문을 읽었다 */
		record Ready(TagSetDefinition definition) implements Draft {
		}

		/** 재평가 기간이 범위 밖이다 */
		record DaysOutOfRange() implements Draft {
		}

		/** 원문이 형식 검증에 걸렸다 */
		record Invalid(List<TagSetProblemView> problems) implements Draft {
		}

	}

	/** 확인 · 활성화의 원문 앞 검사 — 재평가 기간 범위 → 원문 해석 */
	private Draft draft(String text, int recentDays) {
		if (!daysInRange(recentDays)) {
			return new Draft.DaysOutOfRange();
		}
		return switch (this.parser.parse(text)) {
			case TagSetParse.Parsed parsed -> new Draft.Ready(parsed.definition());
			case TagSetParse.Invalid invalid -> new Draft.Invalid(views(invalid.problems()));
		};
	}

	/** 기준 버전과 그 지금 태그 기준 · 저장 기준으로 원문을 대조한다 — 확인 · 활성화 공통 */
	private Revision revise(TagSet base, TagSetDefinition definition) {
		return TagSetRevision.review(base.content(), this.thresholds.current(base.id()), definition);
	}

	/** 재평가 기간에 처음 본 기사 — 기간이 0 이면 읽지 않는다. 경계 시각은 활성화 시각 − 기간 × 24시간이고 포함한다 */
	private List<ArticleId> recent(int recentDays, Instant now) {
		if (recentDays == 0) {
			return List.of();
		}
		return this.articles.firstSeenSince(now.minus(Duration.ofDays(recentDays)));
	}

	private static boolean daysInRange(int recentDays) {
		return recentDays >= 0 && recentDays <= TagSetAdministration.MAX_REEVALUATION_DAYS;
	}

	/** 원문 문제를 공개 타입으로 옮긴다 — 최상위 경로는 빈 문자열, 모르는 위치는 null */
	private static List<TagSetProblemView> views(List<TagSetProblem> problems) {
		return problems.stream()
			.map(problem -> new TagSetProblemView(problem.path().toString(), problem.code().name(), problem.message(),
					problem.location().knownLine(), problem.location().knownColumn()))
			.toList();
	}

	static TagSetVersionView view(TagSet tagSet) {
		return new TagSetVersionView(tagSet.id(), tagSet.number(), tagSet.active(), tagSet.createdBy(),
				tagSet.createdAt());
	}

}
