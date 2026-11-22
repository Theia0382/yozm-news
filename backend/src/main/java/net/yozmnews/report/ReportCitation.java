package net.yozmnews.report;

import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.storyline.StorylineId;

/**
 * 리포트의 인용 대상 · 인용 — 스토리라인 또는 사건 · docs/specs/report.md §3 · report-R3 · report-R7
 */
public sealed interface ReportCitation {

	/** 스토리라인 */
	record Storyline(StorylineId id) implements ReportCitation {

		public Storyline {
			Objects.requireNonNull(id, "id");
		}

	}

	/** 사건 */
	record Event(EventId id) implements ReportCitation {

		public Event {
			Objects.requireNonNull(id, "id");
		}

	}

}
