package net.yozmnews.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.evaluator.MembershipCandidate;
import net.yozmnews.platform.ProbabilityRange;
import org.jspecify.annotations.Nullable;

/**
 * 소속 판정 두 가지(사건 소속 · 스토리라인 소속)의 요청 · 결과 값이 함께 쓰는 검사 — 정본 · docs/specs/evaluation.md §3 · evaluation-R37 ·
 * evaluation-R38.
 * <p>
 * 두 판정은 질문의 뜻만 다르고 요청 · 결과의 모양과 상한이 같다. 상한은 {@link MembershipQuery#MAX_CANDIDATES} ·
 * {@link EventDescriptor#MAX_ARTICLES} 가 정본이다. 요청 검사의 문구는 두 판정에 같고, 후보 하나 · 결과 검사 문구의
 * {@code noun} 은 후보 ID 의 이름(사건 · 후보)이다.
 */
final class MembershipChecks {

	private MembershipChecks() {
	}

	/**
	 * 요청 하나 — 후보가 1 ~ {@link MembershipQuery#MAX_CANDIDATES} 개, 후보 ID 가 겹치지 않고(정본
	 * {@link MembershipCandidate#requireCandidates}), 판정할 기사가 어느 후보의 서술 기사도 아니다.
	 * @throws IllegalArgumentException 어길 때
	 */
	static <T> void query(ArticleId articleId, List<T> candidates, ToLongFunction<T> id,
			Function<T, List<ArticleId>> articles) {
		Objects.requireNonNull(articleId, "articleId");
		MembershipCandidate.requireCandidates(candidates, id);
		for (T candidate : candidates) {
			if (articles.apply(candidate).contains(articleId)) {
				throw new IllegalArgumentException("판정할 기사가 후보 " + id.applyAsLong(candidate) + " 의 서술 기사다");
			}
		}
	}

	/**
	 * 후보 하나 — ID 가 1 이상이고, 서술 기사가 1 ~ {@link EventDescriptor#MAX_ARTICLES} 개이며 겹치지 않는다.
	 * @return 복사한 서술 기사
	 * @throws IllegalArgumentException 어길 때
	 */
	static List<ArticleId> candidate(long id, List<ArticleId> articleIds, String noun) {
		if (id < 1) {
			throw new IllegalArgumentException(noun + " ID 는 1 이상이다: " + id);
		}
		List<ArticleId> copied = List.copyOf(articleIds);
		if (copied.isEmpty()) {
			throw new IllegalArgumentException("서술 기사가 없다");
		}
		if (copied.size() > EventDescriptor.MAX_ARTICLES) {
			throw new IllegalArgumentException("서술 기사는 " + EventDescriptor.MAX_ARTICLES + "개까지다: " + copied.size());
		}
		if (new HashSet<>(copied).size() != copied.size()) {
			throw new IllegalArgumentException("서술 기사가 겹친다");
		}
		return copied;
	}

	/**
	 * 판정한 결과 — 판정 원본은 확률이 있을 때만 있고, 후보 ID 는 1 이상, 확률은 0 ~ 1 의 유한값이다.
	 * @param name 확률 맵의 이름 — 확률 범위 실패 문구에 싣는다
	 * @return 복사한 확률
	 * @throws IllegalArgumentException 어길 때
	 */
	static Map<Long, Double> judged(@Nullable Object judgement, Map<Long, Double> probabilities, String noun,
			String name) {
		Objects.requireNonNull(probabilities, "probabilities");
		if ((judgement == null) != probabilities.isEmpty()) {
			throw new IllegalArgumentException("판정 원본은 확률이 있을 때만 있다: 원본 " + judgement + ", 확률 " + probabilities);
		}
		probabilities.forEach((candidateId, probability) -> {
			Objects.requireNonNull(candidateId, "candidateId");
			Objects.requireNonNull(probability, "probability");
			if (candidateId < 1) {
				throw new IllegalArgumentException(noun + " ID 는 1 이상이다: " + candidateId);
			}
			ProbabilityRange.require(probability, name);
		});
		return Map.copyOf(probabilities);
	}

	/**
	 * 평가기의 예상한 실패 — 문구가 공백뿐이 아니다.
	 * @throws IllegalArgumentException 어길 때
	 */
	static void failed(EvaluatorFailureKind kind, String message) {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
		if (message.isBlank()) {
			throw new IllegalArgumentException("실패 문구가 비었다");
		}
	}

}
