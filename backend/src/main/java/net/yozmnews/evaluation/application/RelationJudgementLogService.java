package net.yozmnews.evaluation.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationJudgementLog;
import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.evaluation.RelationReading;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.relation.RelationCheck;
import net.yozmnews.evaluation.domain.relation.RelationJudgementRepository;
import net.yozmnews.evaluation.domain.relation.StoredRelationJudgement;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link RelationJudgementLog} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R50.
 * <p>
 * 원본을 한 번에 읽고, 평가기가 있으면 원본마다 포트로 다시 읽어 {@link RelationCheck} 로 답 다섯과 대조한다. 평가기는 부를 때 찾는다 —
 * 없으면 원본마다 {@link RelationReading.Unread.Reason#NO_EVALUATOR} 이고 나머지 값은 그대로 낸다. 트랜잭션 · 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Service
class RelationJudgementLogService implements RelationJudgementLog {

	private final Supplier<@Nullable ArticleEvaluator> evaluator;

	private final RelationJudgementRepository judgements;

	@Autowired
	RelationJudgementLogService(ObjectProvider<ArticleEvaluator> evaluators, RelationJudgementRepository judgements) {
		this(evaluators::getIfAvailable, judgements);
	}

	RelationJudgementLogService(Supplier<@Nullable ArticleEvaluator> evaluator,
			RelationJudgementRepository judgements) {
		this.evaluator = evaluator;
		this.judgements = judgements;
	}

	@Override
	public Map<RelationJudgementId, RelationJudgementView> findAll(Collection<RelationJudgementId> ids) {
		Objects.requireNonNull(ids, "ids");
		if (ids.size() > MAX_IDS) {
			throw new IllegalArgumentException("관계 판정 원본은 한 번에 " + MAX_IDS + "건까지 찾는다: " + ids.size());
		}
		if (ids.isEmpty()) {
			return Map.of();
		}
		ArticleEvaluator found = this.evaluator.get();
		Map<RelationJudgementId, RelationJudgementView> views = new LinkedHashMap<>();
		for (StoredRelationJudgement stored : this.judgements.findAll(ids)) {
			views.put(stored.id(),
					new RelationJudgementView(stored.id(), stored.firstFlowId(), stored.secondFlowId(), stored.evaluator(),
							stored.modelVersion(), stored.wordingVersion(), stored.inputTokens(), stored.judgedAt(),
							reading(found, stored)));
		}
		return views;
	}

	private static RelationReading reading(@Nullable ArticleEvaluator evaluator, StoredRelationJudgement stored) {
		if (evaluator == null) {
			return new RelationReading.Unread(RelationReading.Unread.Reason.NO_EVALUATOR,
					"이 프로세스에 평가기가 없어 원본 응답을 읽지 못한다");
		}
		return switch (evaluator.rereadRelation(stored.evaluator(), stored.rawResponse())) {
			case RereadFailure failure -> new RelationReading.Unread(switch (failure.kind()) {
				case OTHER_EVALUATOR -> RelationReading.Unread.Reason.OTHER_EVALUATOR;
				case UNREADABLE -> RelationReading.Unread.Reason.UNREADABLE;
			}, failure.message());
			case Relation relation -> RelationCheck.mismatch(relation)
				.<RelationReading>map(mismatch -> new RelationReading.Unread(RelationReading.Unread.Reason.UNREADABLE,
						mismatch))
				.orElseGet(() -> new RelationReading.Read(relation.probabilities(), relation.firstOneMatter(),
						relation.secondOneMatter()));
		};
	}

}
