package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FlowCandidate;
import net.yozmnews.evaluation.FlowMembership;
import net.yozmnews.evaluation.FlowMembershipJudgement;
import net.yozmnews.evaluation.FlowMembershipJudgementId;
import net.yozmnews.evaluation.FlowMembershipQuery;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import net.yozmnews.evaluation.domain.membership.FlowMembershipJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link FlowMembership} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R51 · evaluation-R52 · evaluation-R44.
 * <p>
 * 판정 한 번의 흐름은 사건 소속 판정과 함께 쓰는 {@link MembershipCall} 이다 — 이 서비스는 흐름 소속 문구
 * ({@link MembershipWording#SAME_MATTER}), 원본 표({@link FlowMembershipJudgementRepository}), 결과 타입을 넘긴다. 포트는 사건 소속과
 * 같은 {@link ArticleEvaluator#judgeMembership} 이다 — 문구만 다르다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class FlowMembershipService implements FlowMembership {

	private static final String NAME = "흐름 소속 판정";

	private static final MembershipCall.Outcomes<FlowMembershipJudgement> OUTCOMES = new MembershipCall.Outcomes<>() {

		@Override
		public FlowMembershipJudgement judged(long judgement, Map<Long, Double> probabilities) {
			return new FlowMembershipJudgement.Judged(new FlowMembershipJudgementId(judgement), probabilities);
		}

		@Override
		public FlowMembershipJudgement nothingAsked() {
			return new FlowMembershipJudgement.Judged(null, Map.of());
		}

		@Override
		public FlowMembershipJudgement failed(EvaluatorFailureKind kind, String message) {
			return new FlowMembershipJudgement.Failed(kind, message);
		}

		@Override
		public FlowMembershipJudgement missing() {
			return new FlowMembershipJudgement.ArticleMissing();
		}

	};

	private final MembershipCall call;

	private final MembershipCall.Recorder recorder;

	@Autowired
	FlowMembershipService(ObjectProvider<ArticleEvaluator> evaluators, ArticleDirectory articles,
			FlowMembershipJudgementRepository judgements, TransactionTemplate transaction) {
		this(evaluators::getIfAvailable, articles, judgements, transaction, InstantSource.system());
	}

	/** 시험이 평가기 · 시계를 넣는다 */
	FlowMembershipService(Supplier<@Nullable ArticleEvaluator> evaluator, ArticleDirectory articles,
			FlowMembershipJudgementRepository judgements, TransactionTemplate transaction, InstantSource clock) {
		this.call = new MembershipCall(evaluator, articles, transaction, clock);
		this.recorder = new MembershipCall.Recorder() {

			@Override
			public long insert(MembershipRecord record) {
				return judgements.insert(record).value();
			}

			@Override
			public void deleteByArticles(Collection<ArticleId> articleIds) {
				judgements.deleteByArticles(articleIds);
			}

		};
	}

	@Override
	public FlowMembershipJudgement judge(FlowMembershipQuery query) {
		Objects.requireNonNull(query, "query");
		Map<Long, List<ArticleId>> candidates = new LinkedHashMap<>();
		for (FlowCandidate candidate : query.candidates()) {
			candidates.put(candidate.flowId(), candidate.articleIds());
		}
		return this.call.judge(NAME, "흐름", query.articleId(), candidates, MembershipWording.SAME_MATTER, this.recorder,
				OUTCOMES);
	}

}
