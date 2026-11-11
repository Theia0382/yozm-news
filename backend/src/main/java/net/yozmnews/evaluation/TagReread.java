package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 태그 판정 원본을 다시 읽은 결과 — docs/specs/evaluation.md §3 · evaluation-R42
 */
public sealed interface TagReread {

	/** 다시 읽었다 — 질문 묶음과 대조한 답 */
	record Read(TagAnswers answers) implements TagReread {

		public Read {
			Objects.requireNonNull(answers, "answers");
		}

	}

	/**
	 * 읽지 못했다.
	 *
	 * @param message 운영자용 문구 — 비밀 값 · 원본 응답 원문을 담지 않는다
	 */
	record Unread(Reason reason, String message) implements TagReread {

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

			/** 다른 평가기가 남긴 원본 — 그 형식을 모른다 */
			OTHER_EVALUATOR,

			/** 이 평가기의 형식이 아니거나 질문 묶음의 답이 없거나 질문 묶음과 어긋나거나, 판정 원본이 없다 */
			UNREADABLE

		}

	}

}
