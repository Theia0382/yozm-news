package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipCheck;
import net.yozmnews.evaluation.domain.membership.MembershipPlan;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link EventMembership} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R37 · evaluation-R38 · evaluation-R44.
 * <p>
 * 트랜잭션 밖에서 판정할 기사와 서술 기사를 한 번에 읽고, 물을 후보를 골라 평가기를 부른다. 성공이면 결과를 물은 후보와 대조한 뒤 짧은
 * 트랜잭션 하나로 판정 원본을 쓰고 그 ID 를 결과에 싣는다. 커밋 뒤 기사가 아직 있는지 다시 본다 — 그새 지워졌으면 방금 쓴 원본을 지운다. 지우기 리스너는 기사 삭제
 * 커밋 뒤에 돌므로, 삭제가 다시 보기보다 앞서면 다시 보기가, 뒤면 리스너가 원본을 지운다.
 * <p>
 * 평가기는 부를 때 찾는다 — 화면 역할만 띄운 프로세스에는 평가기가 없을 수 있고, 판정은 작업 역할의 작업에서 부른다. 트랜잭션 · 역할
 * 애너테이션이 없다(두 역할 공통).
 */
@Service
class EventMembershipService implements EventMembership {

	static final String NO_EVALUATOR = "평가기가 없는 프로세스에서 사건 소속 판정을 불렀다 — 판정은 작업 역할의 작업에서 부른다";

	static final String IN_TRANSACTION = "사건 소속 판정을 트랜잭션 안에서 불렀다 — Jev 호출은 트랜잭션 밖에서 한다";

	private static final Logger logger = LoggerFactory.getLogger(EventMembershipService.class);

	private final Supplier<@Nullable ArticleEvaluator> evaluator;

	private final ArticleDirectory articles;

	private final EventJudgementRepository judgements;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	EventMembershipService(ObjectProvider<ArticleEvaluator> evaluators, ArticleDirectory articles,
			EventJudgementRepository judgements, TransactionTemplate transaction) {
		this(evaluators::getIfAvailable, articles, judgements, transaction, InstantSource.system());
	}

	/** 시험이 평가기 · 시계를 넣는다 */
	EventMembershipService(Supplier<@Nullable ArticleEvaluator> evaluator, ArticleDirectory articles,
			EventJudgementRepository judgements, TransactionTemplate transaction, InstantSource clock) {
		this.evaluator = evaluator;
		this.articles = articles;
		this.judgements = judgements;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public MembershipJudgement judge(MembershipQuery query) {
		Objects.requireNonNull(query, "query");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		ArticleEvaluator evaluator = this.evaluator.get();
		if (evaluator == null) {
			throw new IllegalStateException(NO_EVALUATOR);
		}
		ArticleId articleId = query.articleId();
		Map<ArticleId, ArticleContent> found = this.articles.findAll(readList(query));
		ArticleContent article = found.get(articleId);
		if (article == null) {
			return new MembershipJudgement.ArticleMissing();
		}
		MembershipWording wording = MembershipWording.CURRENT;
		return switch (MembershipPlan.of(query, article, found, wording)) {
			case MembershipPlan.Plan.NothingToAsk nothing -> new MembershipJudgement.Judged(null, Map.of());
			case MembershipPlan.Plan.Ask ask -> {
				MembershipResult result = evaluator.judgeMembership(ask.input());
				yield switch (result) {
					case EvaluationFailure failure -> new MembershipJudgement.Failed(failure.kind(), failure.message());
					case Membership membership -> record(articleId, ask.asked(), membership, wording);
				};
			}
		};
	}

	/** 결과를 대조해 원본을 쓰고, 커밋 뒤 기사가 아직 있는지 다시 본다 */
	private MembershipJudgement record(ArticleId articleId, Map<Long, List<ArticleId>> asked, Membership membership,
			MembershipWording wording) {
		Optional<String> mismatch = MembershipCheck.mismatch(membership, asked.keySet());
		if (mismatch.isPresent()) {
			throw new IllegalStateException("사건 소속 판정 결과를 저장할 수 없다 — " + mismatch.get());
		}
		MembershipRecord record = MembershipRecord.of(articleId, asked, membership, wording.version(),
				this.clock.instant());
		EventJudgementId id = Objects.requireNonNull(this.transaction.execute(status -> this.judgements.insert(record)),
				"id");
		if (!this.articles.findAll(List.of(articleId)).containsKey(articleId)) {
			this.transaction.executeWithoutResult(status -> this.judgements.deleteByArticles(List.of(articleId)));
			logger.info("판정 중 기사가 지워져 판정 원본을 지웠다 — 기사 {}", articleId.value());
			return new MembershipJudgement.ArticleMissing();
		}
		return new MembershipJudgement.Judged(id, membership.probabilities());
	}

	/** 판정할 기사와 모든 서술 기사 — 한 번에 읽는다 */
	private static List<ArticleId> readList(MembershipQuery query) {
		Set<ArticleId> ids = new LinkedHashSet<>();
		ids.add(query.articleId());
		for (EventDescriptor candidate : query.candidates()) {
			ids.addAll(candidate.articleIds());
		}
		return new ArrayList<>(ids);
	}

}
