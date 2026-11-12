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
import net.yozmnews.evaluation.FlowHappening;
import net.yozmnews.evaluation.FlowRelation;
import net.yozmnews.evaluation.RelationJudgement;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationResult;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.relation.RelationCheck;
import net.yozmnews.evaluation.domain.relation.RelationJudgementRepository;
import net.yozmnews.evaluation.domain.relation.RelationPlan;
import net.yozmnews.evaluation.domain.relation.RelationRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link FlowRelation} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R48 · evaluation-R49.
 * <p>
 * 트랜잭션 밖에서 두 흐름의 서술 기사를 한 번에 읽고, 물을 서술을 골라 평가기를 부른다. 성공이면 결과를 답 다섯과 대조한 뒤 짧은 트랜잭션 하나로
 * 판정 원본을 쓰고 그 ID 를 결과에 싣는다. 판정할 기사가 따로 없어 커밋 뒤에 다시 보지 않는다.
 * <p>
 * 평가기는 부를 때 찾는다 — 화면 역할만 띄운 프로세스에는 평가기가 없을 수 있고, 판정은 작업 역할의 작업에서 부른다. 트랜잭션 · 역할
 * 애너테이션이 없다(두 역할 공통).
 */
@Service
class FlowRelationService implements FlowRelation {

	static final String NO_EVALUATOR = "평가기가 없는 프로세스에서 관계 판정을 불렀다 — 판정은 작업 역할의 작업에서 부른다";

	static final String IN_TRANSACTION = "관계 판정을 트랜잭션 안에서 불렀다 — Jev 호출은 트랜잭션 밖에서 한다";

	private final Supplier<@Nullable ArticleEvaluator> evaluator;

	private final ArticleDirectory articles;

	private final RelationJudgementRepository judgements;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	FlowRelationService(ObjectProvider<ArticleEvaluator> evaluators, ArticleDirectory articles,
			RelationJudgementRepository judgements, TransactionTemplate transaction) {
		this(evaluators::getIfAvailable, articles, judgements, transaction, InstantSource.system());
	}

	/** 시험이 평가기 · 시계를 넣는다 */
	FlowRelationService(Supplier<@Nullable ArticleEvaluator> evaluator, ArticleDirectory articles,
			RelationJudgementRepository judgements, TransactionTemplate transaction, InstantSource clock) {
		this.evaluator = evaluator;
		this.articles = articles;
		this.judgements = judgements;
		this.transaction = transaction;
		this.clock = clock;
	}

	@Override
	public RelationJudgement judge(RelationQuery query) {
		Objects.requireNonNull(query, "query");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		ArticleEvaluator evaluator = this.evaluator.get();
		if (evaluator == null) {
			throw new IllegalStateException(NO_EVALUATOR);
		}
		Map<ArticleId, ArticleContent> found = this.articles.findAll(readList(query));
		RelationWording wording = RelationWording.CURRENT;
		return switch (RelationPlan.of(query, found, wording)) {
			case RelationPlan.Plan.NothingToAsk nothing -> new RelationJudgement.NothingToAsk();
			case RelationPlan.Plan.Ask ask -> {
				RelationResult result = evaluator.judgeRelation(ask.input());
				yield switch (result) {
					case EvaluationFailure failure -> new RelationJudgement.Failed(failure.kind(), failure.message());
					case Relation relation -> record(query, ask, relation, wording);
				};
			}
		};
	}

	/** 결과를 대조해 원본을 쓴다 */
	private RelationJudgement record(RelationQuery query, RelationPlan.Plan.Ask ask, Relation relation,
			RelationWording wording) {
		Optional<String> mismatch = RelationCheck.mismatch(relation);
		if (mismatch.isPresent()) {
			throw new IllegalStateException("관계 판정 결과를 저장할 수 없다 — " + mismatch.get());
		}
		RelationRecord record = RelationRecord.of(query.first().flowId(), query.second().flowId(), ask.first(),
				ask.second(), relation, wording.version(), this.clock.instant());
		RelationJudgementId id = Objects
			.requireNonNull(this.transaction.execute(status -> this.judgements.insert(record)), "id");
		return new RelationJudgement.Judged(id, relation.probabilities(), relation.firstOneMatter(),
				relation.secondOneMatter());
	}

	/** 두 흐름의 모든 서술 기사 — 한 번에 읽는다. 두 흐름이 함께 가진 기사는 한 번만 */
	private static List<ArticleId> readList(RelationQuery query) {
		Set<ArticleId> ids = new LinkedHashSet<>();
		for (FlowHappening happening : query.first().described()) {
			ids.add(happening.articleId());
		}
		for (FlowHappening happening : query.second().described()) {
			ids.add(happening.articleId());
		}
		return new ArrayList<>(ids);
	}

}
