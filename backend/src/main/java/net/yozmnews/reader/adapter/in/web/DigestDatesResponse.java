package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.util.List;

import net.yozmnews.digest.DigestDates;
import net.yozmnews.digest.DigestDayState;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 날짜 목록 한 쪽 — 늦은 순. docs/specs/reader.md §5 · reader-R10
 *
 * @param dates 날짜와 그 날짜의 상태
 * @param next 다음 쪽의 {@code before} — 없으면 null
 */
record DigestDatesResponse(List<DigestDateResponse> dates, @Nullable LocalDate next) {

	static DigestDatesResponse of(DigestDates dates) {
		return new DigestDatesResponse(
				dates.dates().stream().map(entry -> new DigestDateResponse(entry.date(), entry.state())).toList(),
				dates.next());
	}

	/**
	 * 날짜 하나.
	 * @param date 다이제스트 날짜
	 * @param state 그 날짜의 상태
	 */
	record DigestDateResponse(LocalDate date, DigestDayState state) {
	}

}
