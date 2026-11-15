package net.yozmnews.user.domain.interest;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * 시간대 읽기의 정본 — {@link ZoneId#getAvailableZoneIds()} 에 있는 시간대 ID 만 받는다. 오프셋({@code +09:00} · {@code Z})과 목록에 없는
 * 약어({@code KST})는 받지 않는다 — docs/specs/user.md §5 · user-R50
 */
public final class Timezones {

	private Timezones() {
	}

	/** 시간대 ID 를 읽는다 — 받지 않는 값이면 빈 값 */
	public static Optional<ZoneId> parse(String raw) {
		Objects.requireNonNull(raw, "raw");
		if (!ZoneId.getAvailableZoneIds().contains(raw)) {
			return Optional.empty();
		}
		return Optional.of(ZoneId.of(raw));
	}

}
