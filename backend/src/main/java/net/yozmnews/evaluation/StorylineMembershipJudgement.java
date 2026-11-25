package net.yozmnews.evaluation;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * 스토리라인 소속 판정 한 번의 결과 — docs/specs/evaluation.md §3 · evaluation-R52
 */
public sealed interface StorylineMembershipJudgement {

	/**
	 * 판정했다 — 물은 후보의 ID → 그 후보와 같은 사안의 전개일 확률. 서술 기사가 모두 지워진 후보는 묻지 않아 없다 — 그런 후보뿐이면
	 * 빈 맵이고 평가기를 부르지 않았다.
	 *
	 * @param judgement 남긴 판정 원본 — 평가기를 부르지 않았으면(확률이 빈 맵) null
	 */
	record Judged(@Nullable StorylineMembershipJudgementId judgement, Map<Long, Double> probabilities)
			implements StorylineMembershipJudgement {

		/**
		 * @throws IllegalArgumentException 후보 ID 가 1 미만이거나 확률이 0 이상 1 이하의 유한값이 아닐 때, 또는 원본이 없는데 확률이
		 * 있거나 원본이 있는데 확률이 없을 때
		 */
		public Judged {
			probabilities = MembershipChecks.judged(judgement, probabilities, "후보");
		}

	}

	/**
	 * 평가기가 예상한 이유로 판정하지 못했다 — 판정 원본을 남기지 않았다.
	 *
	 * @param message 운영자용 문구 — 비밀 값을 담지 않는다
	 */
	record Failed(EvaluatorFailureKind kind, String message) implements StorylineMembershipJudgement {

		/**
		 * @throws IllegalArgumentException 문구가 공백뿐일 때
		 */
		public Failed {
			MembershipChecks.failed(kind, message);
		}

	}

	/** 판정할 기사가 없다 — 부르기 전에 지워졌거나 판정 중에 지워졌다. 판정 원본은 남지 않는다 */
	record ArticleMissing() implements StorylineMembershipJudgement {

	}

}
