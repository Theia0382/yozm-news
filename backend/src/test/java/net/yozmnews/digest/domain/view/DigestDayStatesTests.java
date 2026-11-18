package net.yozmnews.digest.domain.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalDate;
import java.util.List;

import net.yozmnews.digest.DigestDateEntry;
import net.yozmnews.digest.DigestDates;
import net.yozmnews.digest.DigestDayState;
import net.yozmnews.digest.DigestStatus;
import org.junit.jupiter.api.Test;

/**
 * 날짜의 상태와 날짜 목록 한 쪽 — 보이는 다이제스트가 있으면 내용, 없으면 가장 최근 시도의 상태다. 한 쪽은 앞 {@code limit} 개이고 더 있으면 다음
 * 쪽을 마지막 날짜로 둔다.
 * <p>
 * 지키는 규칙: digest-R39
 */
class DigestDayStatesTests {

	@Test
	void aDateWithAShownDigestHasContentWhateverItsLatestAttempt() {
		for (DigestStatus latest : DigestStatus.values()) {
			assertThat(DigestDayStates.of(true, latest)).as(latest.name()).isEqualTo(DigestDayState.CONTENT);
		}
	}

	@Test
	void aDateWithoutAShownDigestTakesItsLatestAttempt() {
		assertThat(DigestDayStates.of(false, DigestStatus.SCHEDULED)).isEqualTo(DigestDayState.IN_PROGRESS);
		assertThat(DigestDayStates.of(false, DigestStatus.RUNNING)).isEqualTo(DigestDayState.IN_PROGRESS);
		assertThat(DigestDayStates.of(false, DigestStatus.EMPTY)).isEqualTo(DigestDayState.EMPTY);
		assertThat(DigestDayStates.of(false, DigestStatus.FAILED)).isEqualTo(DigestDayState.FAILED);
	}

	@Test
	void aReadyLatestAttemptWithoutAShownDigestIsRefused() {
		assertThatIllegalStateException().isThrownBy(() -> DigestDayStates.of(false, DigestStatus.READY));
	}

	@Test
	void aPageTakesTheFirstRowsAndPointsTheNextPageAtItsLastDate() {
		List<DigestDateRow> rows = List.of(new DigestDateRow(LocalDate.parse("2026-11-18"), true, DigestStatus.RUNNING),
				new DigestDateRow(LocalDate.parse("2026-11-17"), false, DigestStatus.FAILED),
				new DigestDateRow(LocalDate.parse("2026-11-15"), false, DigestStatus.EMPTY));

		assertThat(DigestDayStates.page(rows, 2)).isEqualTo(new DigestDates(
				List.of(new DigestDateEntry(LocalDate.parse("2026-11-18"), DigestDayState.CONTENT),
						new DigestDateEntry(LocalDate.parse("2026-11-17"), DigestDayState.FAILED)),
				LocalDate.parse("2026-11-17")));
	}

	@Test
	void theLastPageHasNoNext() {
		List<DigestDateRow> rows = List.of(new DigestDateRow(LocalDate.parse("2026-11-15"), false, DigestStatus.EMPTY));

		assertThat(DigestDayStates.page(rows, 2)).isEqualTo(new DigestDates(
				List.of(new DigestDateEntry(LocalDate.parse("2026-11-15"), DigestDayState.EMPTY)), null));
		assertThat(DigestDayStates.page(List.of(), 2)).isEqualTo(new DigestDates(List.of(), null));
	}

}
