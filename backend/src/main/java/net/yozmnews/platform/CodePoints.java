package net.yozmnews.platform;

import java.util.Objects;

/**
 * 코드 포인트 단위 자르기 — 여러 모듈의 길이 상한이 함께 쓰는 정본 · docs/specs/platform.md §3
 */
public final class CodePoints {

	private CodePoints() {
	}

	/** 앞 {@code max} 코드 포인트를 낸다 — docs/specs/platform.md §3 */
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
