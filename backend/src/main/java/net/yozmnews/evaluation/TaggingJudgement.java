package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 태그 판정 한 번의 결과 — docs/specs/evaluation.md §3 · evaluation-R41
 */
public sealed interface TaggingJudgement {

	/**
	 * 판정했다 — 남긴 판정 원본과 질문마다의 확률.
	 *
	 * @param judgement 남긴 판정 원본 — 판정을 반영한 쪽이 자기 기록에 남긴다
	 */
	record Judged(TagJudgementId judgement, TagAnswers answers) implements TaggingJudgement {

		public Judged {
			Objects.requireNonNull(judgement, "judgement");
			Objects.requireNonNull(answers, "answers");
		}

	}

	/**
	 * 평가기가 예상한 이유로 판정하지 못했다 — 판정 원본을 남기지 않았다.
	 *
	 * @param message 운영자용 문구 — 비밀 값을 담지 않는다
	 */
	record Failed(EvaluatorFailureKind kind, String message) implements TaggingJudgement {

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
	record ArticleMissing() implements TaggingJudgement {

	}

}
