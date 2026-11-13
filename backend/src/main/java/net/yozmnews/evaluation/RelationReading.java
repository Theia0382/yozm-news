package net.yozmnews.evaluation;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 관계 판정 원본의 원본 응답을 다시 읽은 결과 — docs/specs/evaluation.md §3 · evaluation-R50
 */
public sealed interface RelationReading {

	/**
	 * 다시 읽었다 — 답 다섯의 확률과 흐름마다 한 사안의 전개일 확률. 방향은 원본의 first · second 흐름 기준이다.
	 */
	record Read(Map<RelationAnswer, Double> probabilities, double firstOneMatter, double secondOneMatter)
			implements RelationReading {

		/**
		 * @throws IllegalArgumentException 답 다섯이 모두 있지 않거나, 확률이 0 ~ 1 의 유한값이 아닐 때
		 */
		public Read {
			Objects.requireNonNull(probabilities, "probabilities");
			if (!probabilities.keySet().equals(EnumSet.allOf(RelationAnswer.class))) {
				throw new IllegalArgumentException("답 다섯의 확률이 모두 있어야 한다: " + probabilities.keySet());
			}
			probabilities.values().forEach(Read::requireProbability);
			requireProbability(firstOneMatter);
			requireProbability(secondOneMatter);
			probabilities = Map.copyOf(probabilities);
		}

		private static void requireProbability(Double probability) {
			Objects.requireNonNull(probability, "probability");
			if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
				throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다: " + probability);
			}
		}

	}

	/**
	 * 읽지 못했다 — 원본의 나머지 값은 그대로 낸다.
	 *
	 * @param message 운영자용 문구 — 원본 응답 원문을 담지 않는다
	 */
	record Unread(Reason reason, String message) implements RelationReading {

		/**
		 * @throws IllegalArgumentException 문구가 공백뿐일 때
		 */
		public Unread {
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(message, "message");
			if (message.isBlank()) {
				throw new IllegalArgumentException("문구가 비었다");
			}
		}

		/** 읽지 못한 까닭 */
		public enum Reason {

			/** 이 프로세스에 평가기가 없다 */
			NO_EVALUATOR,

			/** 다른 평가기가 남긴 원본이다 — 그 형식을 모른다 */
			OTHER_EVALUATOR,

			/** 이 평가기의 형식이 아니거나 답 다섯 · 한 사안 확률과 어긋난다 */
			UNREADABLE

		}

	}

}
