package net.yozmnews.digest.domain.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestAttempt;
import net.yozmnews.digest.DigestCitation;
import net.yozmnews.digest.DigestDay;
import net.yozmnews.digest.DigestDayUsage;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.event.EventId;
import net.yozmnews.user.GenerationFailureCause;
import org.junit.jupiter.api.Test;

/**
 * 날짜 보기를 만드는 규칙 — 보이는 행이 보이는 다이제스트, ID 가 가장 큰 행이 가장 최근 시도, 시도 수는 행 수, 토큰은 공급자가 답한 행의 합이다.
 * 실패의 갈래는 실패 코드에서 온다. 기대값은 손으로 적은 리터럴이다.
 * <p>
 * 지키는 규칙: digest-R38
 */
class DigestDaysTests {

	private static final LocalDate DATE = LocalDate.parse("2026-11-18");

	private static final Instant START = Instant.parse("2026-11-17T15:00:00Z");

	private static final Instant END = Instant.parse("2026-11-18T15:00:00Z");

	private static final Instant DONE = Instant.parse("2026-11-18T15:02:00Z");

	private static final DigestNeighbors NEIGHBORS = new DigestNeighbors(LocalDate.parse("2026-11-16"), null);

	private static final List<DigestSectionView> SECTIONS = List.of(
			new DigestSectionView(SectionKind.OVERVIEW, null, "개요", null, List.of(), List.of(), null),
			new DigestSectionView(SectionKind.TOP_STORY, "제목", "요약", SelectionReason.MAJOR_STORYLINE,
					List.of(new DigestCitation(new ArticleId(7))), List.of(new EventId(70)), null));

	@Test
	void theShownRowTheLatestRowAndTheTokensOfAnsweredRows() {
		List<DigestDayRow> rows = List.of(
				new DigestDayRow(11, DigestStatus.READY, START, END, "openai", "m", 30, 100, 20, null, DONE, true),
				new DigestDayRow(12, DigestStatus.FAILED, START, END, "openai", "m", 30, null, null,
						DigestFailure.KEY_INVALID, DONE, false),
				new DigestDayRow(13, DigestStatus.RUNNING, START, END, "openai", "m", 30, null, null, null, null,
						false));

		DigestDay day = DigestDays.of(DATE, rows, SECTIONS, NEIGHBORS);

		assertThat(day.date()).isEqualTo(DATE);
		assertThat(day.previousDate()).isEqualTo(LocalDate.parse("2026-11-16"));
		assertThat(day.nextDate()).isNull();
		assertThat(day.shown()).isNotNull();
		assertThat(day.shown().digestId()).isEqualTo(11);
		assertThat(day.shown().inputTokens()).isEqualTo(100);
		assertThat(day.shown().outputTokens()).isEqualTo(20);
		assertThat(day.shown().candidateCount()).isEqualTo(30);
		assertThat(day.shown().sections()).isEqualTo(SECTIONS);
		assertThat(day.latest()).isEqualTo(new DigestAttempt(13, DigestStatus.RUNNING, null, null, null));
		assertThat(day.usage()).isEqualTo(new DigestDayUsage(3, 100, 20));
	}

	@Test
	void aFailedLatestAttemptCarriesItsCodeAndCause() {
		List<DigestDayRow> rows = List.of(new DigestDayRow(12, DigestStatus.FAILED, START, END, "openai", "m", 30, null,
				null, DigestFailure.KEY_INVALID, DONE, false));

		DigestDay day = DigestDays.of(DATE, rows, List.of(), NEIGHBORS);

		assertThat(day.shown()).isNull();
		assertThat(day.latest())
			.isEqualTo(new DigestAttempt(12, DigestStatus.FAILED, DigestFailure.KEY_INVALID, GenerationFailureCause.KEY, DONE));
		assertThat(day.usage()).isEqualTo(new DigestDayUsage(1, 0, 0));
	}

	@Test
	void tokensOfAFailedAttemptTheProviderAnsweredCountTowardTheDay() {
		List<DigestDayRow> rows = List.of(
				new DigestDayRow(11, DigestStatus.READY, START, END, "openai", "m", 30, 100, 20, null, DONE, true),
				new DigestDayRow(12, DigestStatus.FAILED, START, END, "openai", "m", 30, 50, 0,
						DigestFailure.NO_CITATION, DONE, false),
				new DigestDayRow(13, DigestStatus.FAILED, START, END, "openai", "m", 30, null, null,
						DigestFailure.PROVIDER_UNAVAILABLE, DONE, false));

		DigestDay day = DigestDays.of(DATE, rows, SECTIONS, NEIGHBORS);

		assertThat(day.usage()).isEqualTo(new DigestDayUsage(3, 150, 20));
		assertThat(day.latest().cause()).isEqualTo(GenerationFailureCause.TEMPORARY);
	}

	@Test
	void aShownDigestWithoutSectionsStaysShown() {
		List<DigestDayRow> rows = List.of(
				new DigestDayRow(11, DigestStatus.READY, START, END, "openai", "m", 30, 100, 20, null, DONE, true));

		DigestDay day = DigestDays.of(DATE, rows, List.of(), NEIGHBORS);

		assertThat(day.shown()).isNotNull();
		assertThat(day.shown().sections()).isEmpty();
		assertThat(day.latest().digestId()).isEqualTo(11);
	}

	@Test
	void noRowsOrTwoShownRowsAreRefused() {
		DigestDayRow shown = new DigestDayRow(11, DigestStatus.READY, START, END, "openai", "m", 30, 100, 20, null,
				DONE, true);
		DigestDayRow alsoShown = new DigestDayRow(12, DigestStatus.READY, START, END, "openai", "m", 30, 100, 20, null,
				DONE, true);

		assertThatIllegalArgumentException().isThrownBy(() -> DigestDays.of(DATE, List.of(), List.of(), NEIGHBORS));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> DigestDays.of(DATE, List.of(shown, alsoShown), SECTIONS, NEIGHBORS));
	}

	@Test
	void aShownRowMissingWhatAReadyDigestHasIsRefused() {
		DigestDayRow noTokens = new DigestDayRow(11, DigestStatus.READY, START, END, "openai", "m", 30, null, null,
				null, DONE, true);
		DigestDayRow notReady = new DigestDayRow(12, DigestStatus.FAILED, START, END, "openai", "m", 30, 100, 20,
				DigestFailure.NO_CITATION, DONE, true);

		assertThatIllegalStateException().isThrownBy(() -> DigestDays.of(DATE, List.of(noTokens), SECTIONS, NEIGHBORS));
		assertThatIllegalStateException().isThrownBy(() -> DigestDays.of(DATE, List.of(notReady), SECTIONS, NEIGHBORS));
	}

}
