package net.yozmnews.report.domain.material;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.report.domain.input.EventLine;
import net.yozmnews.report.domain.report.ReportPeriod;
import org.jspecify.annotations.Nullable;

/**
 * 사용자 스토리라인의 사건 줄 고르기의 정본 · docs/specs/report.md §5 · report-R16
 * <p>
 * 기간 안 기사가 있고 대표 기사 제목이 있는 사건을 기간 안 기사 수 내림차순 · 첫 기사 시각 · 사건 ID 순으로 {@link #MAX_EVENT_LINES} 개까지
 * 고른다. 첫날은 사용자 시간대의 날짜이고, 첫 기사 시각이 기간 [시작, 끝) 안이면 기간 안에서 시작한 사건이다.
 */
public final class EventLines {

	/** 사용자 스토리라인마다 싣는 사건 줄의 상한 — 측정 전 목표 */
	public static final int MAX_EVENT_LINES = 10;

	private static final Comparator<EventFact> ORDER = Comparator.comparingInt(EventFact::articlesInPeriod)
		.reversed()
		.thenComparing(EventFact::firstArticleAt)
		.thenComparingLong(fact -> fact.event().value());

	private EventLines() {
	}

	/**
	 * 사건 하나의 재료
	 *
	 * @param articlesInPeriod 기간 안 기사 수(사건 배정의 발행 시각)
	 * @param title 대표 기사 제목 — 대표 기사가 없거나 지워졌으면 null
	 */
	public record EventFact(EventId event, Instant firstArticleAt, int articlesInPeriod, @Nullable String title) {

		public EventFact {
			Objects.requireNonNull(event, "event");
			Objects.requireNonNull(firstArticleAt, "firstArticleAt");
		}

	}

	public static List<EventLine> pick(List<EventFact> facts, ReportPeriod period, ZoneId zone) {
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(zone, "zone");
		return facts.stream()
			.filter(fact -> fact.articlesInPeriod() > 0 && fact.title() != null)
			.sorted(ORDER)
			.limit(MAX_EVENT_LINES)
			.map(fact -> new EventLine(fact.event(), Objects.requireNonNull(fact.title()),
					LocalDate.ofInstant(fact.firstArticleAt(), zone), fact.articlesInPeriod(),
					!fact.firstArticleAt().isBefore(period.start()) && fact.firstArticleAt().isBefore(period.end())))
			.toList();
	}

}
