package net.yozmnews.platform;

import java.util.Objects;

/**
 * 확률 범위 — 0 이상 1 이하의 유한값. 여러 모듈의 확률 값이 함께 쓰는 판정의 정본 · docs/specs/platform.md §3
 */
public final class ProbabilityRange {

	private ProbabilityRange() {
	}

	/** 0 이상 1 이하의 유한값인가 — NaN · 무한대는 아니다 · docs/specs/platform.md §3 */
	public static boolean contains(double value) {
		return Double.isFinite(value) && value >= 0 && value <= 1;
	}

	/**
	 * 확률 범위 안의 값만 받는다 — docs/specs/platform.md §3.
	 * @param name 값의 이름 — 레코드 칸이면 {@code 타입.칸}, 함수 인자면 인자 이름. 실패 문구에 싣는다
	 * @return 받은 값 그대로
	 * @throws IllegalArgumentException 값이 범위 밖이거나 이름이 공백뿐일 때
	 */
	public static double require(double value, String name) {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("값의 이름이 비었다");
		}
		if (!contains(value)) {
			throw new IllegalArgumentException("확률은 0 ~ 1 의 유한값이다 — " + name + ": " + value);
		}
		return value;
	}

}
