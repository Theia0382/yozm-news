package net.yozmnews.digest.domain.view;

import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.digest.DigestStatus;

/**
 * 날짜 목록의 재료 — 날짜 하나. docs/specs/digest.md §4 · digest-R39
 *
 * @param date 다이제스트 날짜
 * @param shown 그 날짜에 보이는 다이제스트가 있는가(digest-R13)
 * @param latestStatus 그 날짜의 가장 최근(ID 가 가장 큰) 행의 상태
 */
public record DigestDateRow(LocalDate date, boolean shown, DigestStatus latestStatus) {

	public DigestDateRow {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(latestStatus, "latestStatus");
	}

}
