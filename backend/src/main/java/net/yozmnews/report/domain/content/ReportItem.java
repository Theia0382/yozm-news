package net.yozmnews.report.domain.content;

import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.report.ReportCitation;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 섹션의 인용 한 줄 — 스토리라인 또는 사건 하나를 생성 때의 ID 로 · docs/specs/report.md §6 · report-R9
 */
@Table("report_items")
public record ReportItem(@Nullable Long storylineId, @Nullable Long eventId) {

	/**
	 * @throws IllegalArgumentException 둘 다 있거나 둘 다 없을 때
	 */
	public ReportItem {
		if ((storylineId == null) == (eventId == null)) {
			throw new IllegalArgumentException("스토리라인과 사건 가운데 하나만 가리킨다");
		}
	}

	/** 인용에서 만든다 */
	public static ReportItem of(ReportCitation citation) {
		Objects.requireNonNull(citation, "citation");
		return switch (citation) {
			case ReportCitation.Storyline storyline -> new ReportItem(storyline.id().value(), null);
			case ReportCitation.Event event -> new ReportItem(null, event.id().value());
		};
	}

	/** 가리키는 인용 */
	public ReportCitation citation() {
		return (this.storylineId != null) ? new ReportCitation.Storyline(new StorylineId(this.storylineId))
				: new ReportCitation.Event(new EventId(Objects.requireNonNull(this.eventId, "eventId")));
	}

}
