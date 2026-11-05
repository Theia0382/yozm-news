package net.yozmnews.evaluation;

import java.util.Map;
import java.util.Objects;

/**
 * 사건 소속 판정 한 번의 결과 — docs/specs/evaluation.md §3 · evaluation-R38
 */
public sealed interface MembershipJudgement {

	/**
	 * 판정했다 — 물은 후보의 사건 ID → 그 사건에 속할 확률. 서술 기사가 모두 지워진 후보는 묻지 않아 없다 — 그런 후보뿐이면 빈 맵이고
	 * 평가기를 부르지 않았다.
	 */
	record Judged(Map<Long, Double> probabilities) implements MembershipJudgement {

		/**
		 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나 확률이 0 이상 1 이하의 유한값이 아닐 때
		 */
		public Judged {
			Objects.requireNonNull(probabilities, "probabilities");
			probabilities.forEach((eventId, probability) -> {
				Objects.requireNonNull(eventId, "eventId");
				Objects.requireNonNull(probability, "probability");
				if (eventId < 1) {
					throw new IllegalArgumentException("사건 ID 는 1 이상이다: " + eventId);
				}
				if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
					throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
				}
			});
			probabilities = Map.copyOf(probabilities);
		}

	}

	/**
	 * 평가기가 예상한 이유로 판정하지 못했다 — 판정 원본을 남기지 않았다.
	 *
	 * @param message 운영자용 문구 — 비밀 값을 담지 않는다
	 */
	record Failed(EvaluatorFailureKind kind, String message) implements MembershipJudgement {

		/**
		 * @throws IllegalArgumentException 문구가 공백뿐일 때
		 */
		public Failed {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(message, "message");
			if (message.isBlank()) {
				throw new IllegalArgumentException("실패 문구가 비었다");
			}
		}

	}

	/** 판정할 기사가 없다 — 부르기 전에 지워졌거나 판정 중에 지워졌다. 판정 원본은 남지 않는다 */
	record ArticleMissing() implements MembershipJudgement {

	}

}
