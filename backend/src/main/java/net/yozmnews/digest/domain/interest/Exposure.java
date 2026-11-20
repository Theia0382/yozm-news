package net.yozmnews.digest.domain.interest;

import java.time.Instant;
import java.util.Objects;

/**
 * 창 안에서 사건 하나의 노출 — 가장 늦은 노출의 시각과 몫. docs/specs/digest.md §5 · digest-R25
 *
 * @param at 가장 늦은 노출의 끝낸 시각
 * @param share 그 노출의 몫 — 0 보다 크고 1 이하
 */
public record Exposure(Instant at, double share) {

	public Exposure {
		Objects.requireNonNull(at, "at");
		requireShare(share);
	}

	/**
	 * 노출의 몫이 0 보다 크고 1 이하인지 확인한다 — 노출 행 · 창의 노출 · 노출 사건이 함께 쓰는 정본.
	 * @throws IllegalArgumentException 범위 밖일 때
	 */
	public static double requireShare(double share) {
		if (!(share > 0.0 && share <= 1.0)) {
			throw new IllegalArgumentException("몫은 0 보다 크고 1 이하다: " + share);
		}
		return share;
	}

}
