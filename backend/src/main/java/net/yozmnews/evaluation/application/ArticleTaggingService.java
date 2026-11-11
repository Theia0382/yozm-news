package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.TagAnswers;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TagReread;
import net.yozmnews.evaluation.TaggingJudgement;
import net.yozmnews.evaluation.TaggingQuery;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import net.yozmnews.evaluation.domain.evaluator.ResultCheck;
import net.yozmnews.evaluation.domain.tagging.StoredTagJudgement;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRecord;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRepository;
import net.yozmnews.evaluation.domain.tagging.TaggingInput;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link ArticleTagging} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R40 ~ evaluation-R42.
 * <p>
 * 판정은 트랜잭션 밖에서 기사와 엔트리 피드의 이름 · 섹션을 읽어 평가기를 부른다. 성공이면 결과를 질문 묶음과 대조한 뒤 짧은 트랜잭션
 * 하나로 판정 원본을 쓰고 그 ID 를 결과에 싣는다. 커밋 뒤 기사가 아직 있는지 다시 본다 — 그새 지워졌으면 방금 쓴 원본을 지운다. 지우기
 * 리스너는 기사 삭제 커밋 뒤에 돌므로, 삭제가 다시 보기보다 앞서면 다시 보기가, 뒤면 리스너가 원본을 지운다(evaluation-R44).
 * <p>
 * 다시 읽기 · 조회는 트랜잭션 애너테이션이 없다 — 부르는 쪽 트랜잭션 안에서 읽고, 평가기 예외가 부르는 쪽 트랜잭션을 롤백 전용으로 만들지
 * 않는다. 평가기는 부를 때 찾는다 — 화면 역할만 띄운 프로세스에는 평가기가 없을 수 있다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ArticleTaggingService implements ArticleTagging {

	static final String NO_EVALUATOR = "평가기가 없는 프로세스에서 태그 판정을 불렀다 — 판정은 작업 역할의 작업에서 부른다";

	static final String IN_TRANSACTION = "태그 판정을 트랜잭션 안에서 불렀다 — Jev 호출은 트랜잭션 밖에서 한다";

	private static final Logger logger = LoggerFactory.getLogger(ArticleTaggingService.class);

	private final Supplier<@Nullable ArticleEvaluator> evaluator;

	private final ArticleDirectory articles;

	private final FeedLabels feedLabels;

	private final TagJudgementRepository judgements;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	ArticleTaggingService(ObjectProvider<ArticleEvaluator> evaluators, ArticleDirectory articles,
			FeedLabels feedLabels, TagJudgementRepository judgements, TransactionTemplate transaction) {
		this(evaluators::getIfAvailable, articles, feedLabels, judgements, transaction, InstantSource.system());
	}

	/** 시험이 평가기 · 시계를 넣는다 */
	ArticleTaggingService(Supplier<@Nullable ArticleEvaluator> evaluator, ArticleDirectory articles,
			FeedLabels feedLabels, TagJudgementRepository judgements, TransactionTemplate transaction,
			InstantSource clock) {
		this.evaluator = evaluator;
		this.articles = articles;
		this.feedLabels = feedLabels;
		this.judgements = judgements;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public TaggingJudgement judge(TaggingQuery query) {
		Objects.requireNonNull(query, "query");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		ArticleEvaluator evaluator = requireEvaluator();
		ArticleId articleId = query.articleId();
		@Nullable ArticleContent content = this.articles.findAll(List.of(articleId)).get(articleId);
		if (content == null) {
			return new TaggingJudgement.ArticleMissing();
		}
		Set<FeedId> feeds = content.entries().stream().map(ArticleEntryView::feedId).collect(Collectors.toSet());
		EvaluationResult result = evaluator.evaluate(TaggingInput.of(content, this.feedLabels.labels(feeds)),
				query.questions());
		return switch (result) {
			case EvaluationFailure failure -> new TaggingJudgement.Failed(failure.kind(), failure.message());
			case Evaluation evaluation -> record(articleId, evaluation, query.questions());
		};
	}

	@Override
	public TagReread reread(TagJudgementId judgement, TagQuestions questions) {
		Objects.requireNonNull(judgement, "judgement");
		Objects.requireNonNull(questions, "questions");
		ArticleEvaluator evaluator = requireEvaluator();
		Optional<StoredTagJudgement> stored = this.judgements.find(judgement);
		if (stored.isEmpty()) {
			return new TagReread.Unread(TagReread.Unread.Reason.UNREADABLE,
					"태그 판정 원본이 없다 — 원본 " + judgement.value());
		}
		RereadResult result = evaluator.reread(stored.get().evaluator(), stored.get().rawResponse(), questions);
		return switch (result) {
			case RereadFailure failure -> new TagReread.Unread(switch (failure.kind()) {
				case OTHER_EVALUATOR -> TagReread.Unread.Reason.OTHER_EVALUATOR;
				case UNREADABLE -> TagReread.Unread.Reason.UNREADABLE;
			}, failure.message());
			case Evaluation evaluation -> ResultCheck.mismatch(evaluation, questions)
				.<TagReread>map(mismatch -> new TagReread.Unread(TagReread.Unread.Reason.UNREADABLE, mismatch))
				.orElseGet(() -> new TagReread.Read(answers(evaluation)));
		};
	}

	@Override
	public Map<TagJudgementId, TagJudgementView> find(Collection<TagJudgementId> judgements) {
		Objects.requireNonNull(judgements, "judgements");
		return this.judgements.views(judgements);
	}

	/** 결과를 대조해 원본을 쓰고, 커밋 뒤 기사가 아직 있는지 다시 본다 */
	private TaggingJudgement record(ArticleId articleId, Evaluation evaluation, TagQuestions questions) {
		Optional<String> mismatch = ResultCheck.mismatch(evaluation, questions);
		if (mismatch.isPresent()) {
			throw new IllegalStateException("태그 판정 결과를 저장할 수 없다 — " + mismatch.get());
		}
		TagJudgementRecord record = TagJudgementRecord.of(articleId, evaluation, this.clock.instant());
		TagJudgementId id = Objects.requireNonNull(this.transaction.execute(status -> this.judgements.insert(record)),
				"id");
		if (!this.articles.findAll(List.of(articleId)).containsKey(articleId)) {
			this.transaction.executeWithoutResult(status -> this.judgements.delete(id));
			logger.info("태그 판정 중 기사가 지워져 판정 원본을 지웠다 — 기사 {}", articleId.value());
			return new TaggingJudgement.ArticleMissing();
		}
		return new TaggingJudgement.Judged(id, answers(evaluation));
	}

	private ArticleEvaluator requireEvaluator() {
		ArticleEvaluator evaluator = this.evaluator.get();
		if (evaluator == null) {
			throw new IllegalStateException(NO_EVALUATOR);
		}
		return evaluator;
	}

	private static TagAnswers answers(Evaluation evaluation) {
		return new TagAnswers(evaluation.topics(), evaluation.regions(), evaluation.formats(), evaluation.importance());
	}

}
