package net.yozmnews.user.domain.preference;

import java.util.List;
import java.util.Objects;

/**
 * 따라가기의 결과 — 활성 버전의 키로 쓴 선호와 조정 항목 — docs/specs/user.md §5 · user-R47
 */
public record FollowResult(PreferenceSet next, List<Adjustment> adjustments) {

	public FollowResult {
		Objects.requireNonNull(next, "next");
		adjustments = List.copyOf(Objects.requireNonNull(adjustments, "adjustments"));
	}

}
