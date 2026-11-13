package net.yozmnews.evaluation.domain.evaluator;

import java.util.Objects;

/**
 * 예상한 이유로 원본 응답을 다시 읽지 못했다 — docs/specs/evaluation.md §4.
 *
 * @param message 운영자용 문구 — 비밀 값 · 원본 응답 원문을 담지 않는다
 */
public record RereadFailure(Kind kind, String message) implements RereadResult, RelationRereadResult {

	/**
	 * @throws IllegalArgumentException 문구가 공백뿐일 때
	 */
	public RereadFailure {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(message, "message");
		if (message.isBlank()) {
			throw new IllegalArgumentException("실패 문구가 비었다");
		}
	}

	/**
	 * 실패 갈래 — 태그 판정 원본이면 채움이 건너뛴 평가를 세는 기준, 관계 판정 원본이면 읽지 못한 까닭 · docs/specs/evaluation.md §4
	 */
	public enum Kind {

		/** 다른 평가기가 남긴 원본 응답이다 — 그 형식을 모른다 */
		OTHER_EVALUATOR,
		/** 원본 응답이 이 평가기의 형식이 아니거나, 태그 판정이면 넘긴 질문 묶음의 답이 없고 관계 판정이면 답 다섯 · 한 사안 확률이 없다 */
		UNREADABLE

	}

}
