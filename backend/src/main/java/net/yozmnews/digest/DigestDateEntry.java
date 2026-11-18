package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 날짜 목록의 날짜 하나 — 그 날짜에 무엇이 보이는가. docs/specs/digest.md §3 · digest-R39
 *
 * @param date 다이제스트 날짜
 * @param state 그 날짜의 상태
 */
public record DigestDateEntry(LocalDate date, DigestDayState state) {

	public DigestDateEntry {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(state, "state");
	}

}
