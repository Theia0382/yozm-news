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
import net.yozmnews.evaluation.EventDescriptor;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventMembership;
import net.yozmnews.evaluation.MembershipJudgement;
import net.yozmnews.evaluation.MembershipQuery;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공개 API {@link EventMembership} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R37 · evaluation-R38 · evaluation-R44.
 * <p>
 * 판정 한 번의 흐름은 흐름 소속 판정과 함께 쓰는 {@link MembershipCall} 이다 — 이 서비스는 사건 소속 문구
 * ({@link MembershipWording#CURRENT}), 원본 표({@link EventJudgementRepository}), 결과 타입을 넘긴다. 트랜잭션 · 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Service
class EventMembershipService implements EventMembership {

	private static final String NAME = "사건 소속 판정";

	private static final MembershipCall.Outcomes<MembershipJudgement> OUTCOMES = new MembershipCall.Outcomes<>() {

		@Override
		public MembershipJudgement judged(long judgement, Map<Long, Double> probabilities) {
			return new MembershipJudgement.Judged(new EventJudgementId(judgement), probabilities);
		}

		@Override
		public MembershipJudgement nothingAsked() {
			return new MembershipJudgement.Judged(null, Map.of());
		}

		@Override
		public MembershipJudgement failed(EvaluatorFailureKind kind, String message) {
			return new MembershipJudgement.Failed(kind, message);
		}

		@Override
		public MembershipJudgement missing() {
			return new MembershipJudgement.ArticleMissing();
		}

	};

	private final MembershipCall call;

	private final MembershipCall.Recorder recorder;

	@Autowired
	EventMembershipService(ObjectProvider<ArticleEvaluator> evaluators, ArticleDirectory articles,
			EventJudgementRepository judgements, TransactionTemplate transaction) {
		this(evaluators::getIfAvailable, articles, judgements, transaction, InstantSource.system());
	}

	/** 시험이 평가기 · 시계를 넣는다 */
	EventMembershipService(Supplier<@Nullable ArticleEvaluator> evaluator, ArticleDirectory articles,
			EventJudgementRepository judgements, TransactionTemplate transaction, InstantSource clock) {
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
	public MembershipJudgement judge(MembershipQuery query) {
		Objects.requireNonNull(query, "query");
		Map<Long, List<ArticleId>> candidates = new LinkedHashMap<>();
		for (EventDescriptor candidate : query.candidates()) {
			candidates.put(candidate.eventId(), candidate.articleIds());
		}
		return this.call.judge(NAME, "사건", query.articleId(), candidates, MembershipWording.CURRENT, this.recorder,
				OUTCOMES);
	}

}
