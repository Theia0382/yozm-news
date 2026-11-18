package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 날짜 목록 한 쪽 — 늦은 순. docs/specs/digest.md §3 · digest-R39
 *
 * @param dates 날짜와 그 날짜의 상태
 * @param next 다음 쪽이 있으면 그 쪽의 {@code before} 로 넘길 날짜(이 쪽의 마지막 날짜) — 없으면 null
 */
public record DigestDates(List<DigestDateEntry> dates, @Nullable LocalDate next) {

	public DigestDates {
		dates = List.copyOf(Objects.requireNonNull(dates, "dates"));
	}

}
