package net.yozmnews.evaluation.application;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
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
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import net.yozmnews.evaluation.domain.membership.MembershipCheck;
import net.yozmnews.evaluation.domain.membership.MembershipPlan;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소속 판정 한 번의 흐름 — 사건 소속({@link EventMembershipService}) · 흐름 소속({@link FlowMembershipService})이 함께 쓰는 정본 ·
 * docs/specs/evaluation.md §4 · evaluation-R37 · evaluation-R38 · evaluation-R44 · evaluation-R52.
 * <p>
 * 트랜잭션 밖에서 판정할 기사와 서술 기사를 한 번에 읽고, 물을 후보를 골라 평가기를 부른다. 성공이면 결과를 물은 후보와 대조한 뒤 짧은
 * 트랜잭션 하나로 판정 원본을 쓰고 그 ID 를 결과에 싣는다. 커밋 뒤 기사가 아직 있는지 다시 본다 — 그새 지워졌으면 방금 쓴 원본을 지운다.
 * 지우기 리스너는 기사 삭제 커밋 뒤에 돌므로, 삭제가 다시 보기보다 앞서면 다시 보기가, 뒤면 리스너가 원본을 지운다. 두 판정이 다른 것은
 * 문구 · 원본 표 · 결과 타입 · 운영자 문구의 이름뿐이다.
 * <p>
 * 평가기는 부를 때 찾는다 — 화면 역할만 띄운 프로세스에는 평가기가 없을 수 있고, 판정은 작업 역할의 작업에서 부른다.
 */
final class MembershipCall {

	private static final Logger logger = LoggerFactory.getLogger(MembershipCall.class);

	private final Supplier<@Nullable ArticleEvaluator> evaluator;

	private final ArticleDirectory articles;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	MembershipCall(Supplier<@Nullable ArticleEvaluator> evaluator, ArticleDirectory articles,
			TransactionTemplate transaction, InstantSource clock) {
		this.evaluator = evaluator;
		this.articles = articles;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 판정 한 번.
	 * @param name 운영자 문구의 판정 이름 — 예 "사건 소속 판정"
	 * @param noun 후보의 이름 — 예 "사건"
	 * @param candidates 후보 ID → 서술 기사 — 요청 순서. 요청 값이 이미 검사한 것이다
	 * @throws IllegalStateException 트랜잭션 안이거나 평가기가 없는 프로세스에서 불렀을 때, 결과가 물은 후보와 어긋날 때
	 */
	<R> R judge(String name, String noun, ArticleId articleId, Map<Long, List<ArticleId>> candidates, MembershipWording wording,
			Recorder recorder, Outcomes<R> outcomes) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(name + "을 트랜잭션 안에서 불렀다 — Jev 호출은 트랜잭션 밖에서 한다");
		}
		ArticleEvaluator evaluator = this.evaluator.get();
		if (evaluator == null) {
			throw new IllegalStateException("평가기가 없는 프로세스에서 " + name + "을 불렀다 — 판정은 작업 역할의 작업에서 부른다");
		}
		Map<ArticleId, ArticleContent> found = this.articles.findAll(readList(articleId, candidates));
		ArticleContent article = found.get(articleId);
		if (article == null) {
			return outcomes.missing();
		}
		return switch (MembershipPlan.of(candidates, article, found, wording)) {
			case MembershipPlan.Plan.NothingToAsk nothing -> outcomes.nothingAsked();
			case MembershipPlan.Plan.Ask ask -> {
				MembershipResult result = evaluator.judgeMembership(ask.input());
				yield switch (result) {
					case EvaluationFailure failure -> outcomes.failed(failure.kind(), failure.message());
					case Membership membership ->
						record(name, noun, articleId, ask.asked(), membership, wording, recorder, outcomes);
				};
			}
		};
	}

	/** 결과를 대조해 원본을 쓰고, 커밋 뒤 기사가 아직 있는지 다시 본다 */
	private <R> R record(String name, String noun, ArticleId articleId, Map<Long, List<ArticleId>> asked, Membership membership,
			MembershipWording wording, Recorder recorder, Outcomes<R> outcomes) {
		Optional<String> mismatch = MembershipCheck.mismatch(membership, asked.keySet(), noun);
		if (mismatch.isPresent()) {
			throw new IllegalStateException(name + " 결과를 저장할 수 없다 — " + mismatch.get());
		}
		MembershipRecord record = MembershipRecord.of(articleId, asked, membership, wording.version(),
				this.clock.instant());
		Long id = this.transaction.execute(status -> recorder.insert(record));
		long judgement = Objects.requireNonNull(id, "id");
		if (!this.articles.findAll(List.of(articleId)).containsKey(articleId)) {
			this.transaction.executeWithoutResult(status -> recorder.deleteByArticles(List.of(articleId)));
			logger.info("{} 중 기사가 지워져 판정 원본을 지웠다 — 기사 {}", name, articleId.value());
			return outcomes.missing();
		}
		return outcomes.judged(judgement, membership.probabilities());
	}

	/** 판정할 기사와 모든 서술 기사 — 한 번에 읽는다 */
	private static List<ArticleId> readList(ArticleId articleId, Map<Long, List<ArticleId>> candidates) {
		Set<ArticleId> ids = new LinkedHashSet<>();
		ids.add(articleId);
		candidates.values().forEach(ids::addAll);
		return new ArrayList<>(ids);
	}

	/** 판정 원본 표 하나 — 넣기와 지운 기사 따르기 */
	interface Recorder {

		/** @return 넣은 행의 ID */
		long insert(MembershipRecord record);

		void deleteByArticles(Collection<ArticleId> articleIds);

	}

	/** 판정 하나의 결과 타입을 만든다 */
	interface Outcomes<R> {

		/** 판정했다 — 남긴 원본 ID 와 물은 후보마다의 확률 */
		R judged(long judgement, Map<Long, Double> probabilities);

		/** 서술이 남은 후보가 없어 평가기를 부르지 않았다 */
		R nothingAsked();

		R failed(EvaluatorFailureKind kind, String message);

		/** 판정할 기사가 없다 */
		R missing();

	}

}
