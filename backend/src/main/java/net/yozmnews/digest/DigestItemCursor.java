package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 다이제스트 항목 목록의 다음 쪽 커서 — 앞 쪽 마지막 항목의 날짜와 섹션 위치. docs/specs/digest.md §3
 *
 * @param date 다이제스트 날짜
 * @param position 그 다이제스트 안의 섹션 위치(0 이상)
 */
public record DigestItemCursor(LocalDate date, int position) {

	public DigestItemCursor {
		Objects.requireNonNull(date, "date");
		if (position < 0) {
			throw new IllegalArgumentException("섹션 위치는 0 이상이다: " + position);
		}
	}

}
