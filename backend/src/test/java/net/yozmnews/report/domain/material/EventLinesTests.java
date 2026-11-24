package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import net.yozmnews.event.EventId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.domain.input.EventLine;
import net.yozmnews.report.domain.material.EventLines.EventFact;
import net.yozmnews.report.domain.report.ReportPeriod;
import org.junit.jupiter.api.Test;

/**
 * 사건 줄 고르기 — 순서 · 상한 · 빼는 사건 · 사용자 시간대의 날짜 · 기간 안 시작. 지키는 규칙: report-R16
 */
class EventLinesTests {

	/** 서울 시간대의 주간 [11-16 00:00, 11-23 00:00) */
	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-15T15:00:00Z"), Instant.parse("2026-11-22T15:00:00Z"));

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	void ordersByArticlesThenFirstArticleThenIdAndLeavesOutEventsWithoutArticlesOrTitle() {
		List<EventLine> lines = EventLines.pick(List.of(
				new EventFact(new EventId(5), Instant.parse("2026-11-17T00:00:00Z"), 2, "다섯"),
				new EventFact(new EventId(4), Instant.parse("2026-11-16T00:00:00Z"), 2, "넷"),
				new EventFact(new EventId(3), Instant.parse("2026-11-16T00:00:00Z"), 2, "셋"),
				new EventFact(new EventId(2), Instant.parse("2026-11-18T00:00:00Z"), 7, "둘"),
				new EventFact(new EventId(1), Instant.parse("2026-11-18T00:00:00Z"), 0, "기간 밖"),
				new EventFact(new EventId(6), Instant.parse("2026-11-18T00:00:00Z"), 9, null)), WEEK, SEOUL);

		assertThat(lines).extracting(line -> line.event().value()).containsExactly(2L, 3L, 4L, 5L);
		assertThat(lines).extracting(EventLine::title).containsExactly("둘", "셋", "넷", "다섯");
		assertThat(lines).extracting(EventLine::articlesInPeriod).containsExactly(7, 2, 2, 2);
	}

	@Test
	void keepsAtMostTheLimit() {
		List<EventFact> facts = new ArrayList<>();
		IntStream.rangeClosed(1, 12)
			.forEach(id -> facts.add(new EventFact(new EventId(id), Instant.parse("2026-11-18T00:00:00Z"), 1, "줄")));

		assertThat(EventLines.pick(facts, WEEK, SEOUL)).hasSize(10)
			.extracting(line -> line.event().value())
			.containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
	}

	@Test
	void writesTheFirstDayInTheUsersZoneAndTellsWhetherTheEventStartedInThePeriod() {
		Instant late = Instant.parse("2026-11-23T15:30:00Z");
		assertThat(EventLines.pick(List.of(new EventFact(new EventId(1), late, 1, "줄")), WEEK, SEOUL).getFirst().firstDay())
			.isEqualTo(LocalDate.of(2026, 11, 24));
		assertThat(EventLines
			.pick(List.of(new EventFact(new EventId(1), late, 1, "줄")), WEEK, ZoneId.of("America/Los_Angeles"))
			.getFirst()
			.firstDay()).isEqualTo(LocalDate.of(2026, 11, 23));

		// 기간 시작 순간은 기간 안, 그 1초 앞은 기간 앞에서 시작한 사건이다
		List<EventLine> lines = EventLines.pick(List.of(
				new EventFact(new EventId(1), Instant.parse("2026-11-15T15:00:00Z"), 2, "시작 순간"),
				new EventFact(new EventId(2), Instant.parse("2026-11-15T14:59:59Z"), 1, "1초 앞")), WEEK, SEOUL);
		assertThat(lines).extracting(EventLine::startedInPeriod).containsExactly(true, false);
		assertThat(lines).extracting(EventLine::firstDay)
			.containsExactly(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 15));
	}

}
