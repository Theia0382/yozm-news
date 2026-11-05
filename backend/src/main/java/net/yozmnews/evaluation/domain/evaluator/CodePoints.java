package net.yozmnews.evaluation.domain.evaluator;

import java.util.Objects;

/**
 * 코드 포인트 단위 자르기 — 평가 입력의 길이 상한이 함께 쓰는 정본 · docs/specs/evaluation.md §4.
 */
public final class CodePoints {

	private CodePoints() {
	}

	/**
	 * 앞 {@code max} 코드 포인트 — 서로게이트 쌍을 가르지 않는다. 그 이하면 그대로 돌려준다.
	 * @throws IllegalArgumentException {@code max} 가 음수일 때
	 */
	public static String prefix(String text, int max) {
		Objects.requireNonNull(text, "text");
		if (max < 0) {
			throw new IllegalArgumentException("상한이 음수다: " + max);
		}
		if (text.codePointCount(0, text.length()) <= max) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, max));
	}

}
