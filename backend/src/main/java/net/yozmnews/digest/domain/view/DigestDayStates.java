package net.yozmnews.digest.domain.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.DigestDateEntry;
import net.yozmnews.digest.DigestDates;
import net.yozmnews.digest.DigestDayState;
import net.yozmnews.digest.DigestStatus;
import org.jspecify.annotations.Nullable;

/**
 * 날짜의 상태와 날짜 목록 한 쪽 — 정본. docs/specs/digest.md §5 · digest-R39
 */
public final class DigestDayStates {

	private DigestDayStates() {
	}

	/**
	 * 날짜의 상태 — 보이는 다이제스트가 있으면 {@link DigestDayState#CONTENT}, 없으면 가장 최근 시도의 상태로 정한다.
	 * @throws IllegalStateException 보이는 다이제스트가 없는데 가장 최근 시도가 {@code READY} 일 때 — {@code READY} 가 있으면 보이는
	 *         다이제스트도 있다
	 */
	public static DigestDayState of(boolean shown, DigestStatus latest) {
		Objects.requireNonNull(latest, "latest");
		if (shown) {
			return DigestDayState.CONTENT;
		}
		return switch (latest) {
			case SCHEDULED, RUNNING -> DigestDayState.IN_PROGRESS;
			case EMPTY -> DigestDayState.EMPTY;
			case FAILED -> DigestDayState.FAILED;
			case READY -> throw new IllegalStateException("READY 가 가장 최근인 날짜에 보이는 다이제스트가 없다");
		};
	}

	/**
	 * 날짜 목록 한 쪽 — 늦은 순으로 읽은 행에서 앞 {@code limit} 개를 싣고, 더 있으면 다음 쪽을 이 쪽의 마지막 날짜로 둔다.
	 * @param rows 늦은 순으로 {@code limit + 1} 개까지 읽은 행
	 */
	public static DigestDates page(List<DigestDateRow> rows, int limit) {
		Objects.requireNonNull(rows, "rows");
		List<DigestDateEntry> entries = rows.stream()
			.limit(limit)
			.map(row -> new DigestDateEntry(row.date(), of(row.shown(), row.latestStatus())))
			.toList();
		@Nullable LocalDate next = (rows.size() > limit) ? entries.getLast().date() : null;
		return new DigestDates(entries, next);
	}

}
