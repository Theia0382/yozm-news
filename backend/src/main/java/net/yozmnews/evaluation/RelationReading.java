package net.yozmnews.evaluation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.platform.ProbabilityRange;

/**
 * 관계 판정 원본의 원본 응답을 다시 읽은 결과 — docs/specs/evaluation.md §3 · evaluation-R50
 */
public sealed interface RelationReading {

	/**
	 * 다시 읽었다 — 그 원본을 남긴 문구 판의 답마다 확률과 스토리라인마다 한 사안의 전개일 확률. 방향은 원본의 first · second 스토리라인 기준이다.
	 * 문구 판마다 답이 다르다 — 옛 판의 원본에는 같은 사안의 조각({@link RelationAnswer#SAME_MATTER})이 없다.
	 */
	record Read(Map<RelationAnswer, Double> probabilities, double firstOneMatter, double secondOneMatter)
			implements RelationReading {

		/**
		 * @throws IllegalArgumentException 답이 어느 문구 판의 답과도 같지 않거나, 확률이 0 ~ 1 의 유한값이 아닐 때
		 */
		public Read {
			Objects.requireNonNull(probabilities, "probabilities");
			Set<RelationAnswer> answers = probabilities.keySet();
			if (RelationWording.KNOWN.stream().noneMatch(wording -> wording.answers().equals(answers))) {
				throw new IllegalArgumentException("한 문구 판의 답이 모두 있어야 한다: " + answers);
			}
			probabilities.values().forEach(probability -> ProbabilityRange.require(
					Objects.requireNonNull(probability, "probability"), "RelationReading.Read.probabilities"));
			ProbabilityRange.require(firstOneMatter, "RelationReading.Read.firstOneMatter");
			ProbabilityRange.require(secondOneMatter, "RelationReading.Read.secondOneMatter");
			probabilities = Map.copyOf(probabilities);
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

			/** 이 평가기의 형식이 아니거나, 모르는 문구 판이거나, 그 판의 답 · 한 사안 확률과 어긋난다 */
			UNREADABLE

		}

	}

}
