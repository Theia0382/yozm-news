package net.yozmnews.report.domain.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPage;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import net.yozmnews.report.ReportView;
import net.yozmnews.storyline.StorylineId;

/**
 * 리포트 조회의 풀기 · 숨기기 · 쪽 나누기 — 정본 · docs/specs/report.md §5 · report-R11 · report-R12
 * <p>
 * 저장한 인용을 풀기 결과(지금 사건 · 지금 스토리라인)로 바꾸고 결과에 없는 것(지운 것)은 뺀다. 인용이 하나 이상 있었는데 하나도 남지 않은 갈래 섹션은
 * 숨기고, 갈래 섹션이 하나 이상 있었고 모두 숨었으면 개요도 숨긴다. 행을 고치지 않는다.
 */
public final class ReportViews {

	private ReportViews() {
	}

	/**
	 * 목록 한 쪽 — {@code size + 1} 개를 읽은 행에서 앞 {@code size} 개와, 더 있으면 이 쪽의 마지막 ID 를 다음 쪽의 {@code before} 로
	 */
	public static ReportPage page(List<ReportRow> rows, int size) {
		Objects.requireNonNull(rows, "rows");
		List<ReportSummary> summaries = rows.stream().limit(size).map(ReportRow::summary).toList();
		ReportId next = (rows.size() > size && !summaries.isEmpty()) ? summaries.getLast().id() : null;
		return new ReportPage(summaries, next);
	}

	/**
	 * 리포트 한 편 — {@code READY} 가 아니면 섹션이 없다
	 * @param events 저장한 사건 → 지금 사건(지운 사건은 없다)
	 * @param storylines 저장한 스토리라인 → 지금 스토리라인(지운 스토리라인은 없다)
	 */
	public static ReportView view(ReportRow row, List<StoredSection> stored, Map<EventId, EventId> events,
			Map<StorylineId, StorylineId> storylines) {
		Objects.requireNonNull(row, "row");
		Objects.requireNonNull(stored, "stored");
		Objects.requireNonNull(events, "events");
		Objects.requireNonNull(storylines, "storylines");
		List<ReportSectionView> sections = (row.summary().status() == ReportStatus.READY)
				? visible(stored, events, storylines) : List.of();
		return new ReportView(row.summary(), row.provider(), row.model(), row.inputTokens(), row.outputTokens(),
				row.statsSnapshot(), sections);
	}

	private static List<ReportSectionView> visible(List<StoredSection> stored, Map<EventId, EventId> events,
			Map<StorylineId, StorylineId> storylines) {
		List<ReportSectionView> shown = new ArrayList<>();
		StoredSection overview = null;
		int kindSections = 0;
		int hidden = 0;
		for (StoredSection section : stored) {
			if (section.kind() == ReportSectionKind.OVERVIEW) {
				overview = section;
				continue;
			}
			kindSections++;
			List<ReportCitation> resolved = resolve(section.citations(), events, storylines);
			if (!section.citations().isEmpty() && resolved.isEmpty()) {
				hidden++;
				continue;
			}
			shown.add(new ReportSectionView(section.kind(), section.title(), section.body(), resolved));
		}
		boolean everyKindSectionHidden = kindSections > 0 && hidden == kindSections;
		if (overview != null && !everyKindSectionHidden) {
			shown.addFirst(new ReportSectionView(ReportSectionKind.OVERVIEW, null, overview.body(), List.of()));
		}
		return List.copyOf(shown);
	}

	private static List<ReportCitation> resolve(List<ReportCitation> citations, Map<EventId, EventId> events,
			Map<StorylineId, StorylineId> storylines) {
		Set<ReportCitation> resolved = new LinkedHashSet<>();
		for (ReportCitation citation : citations) {
			switch (citation) {
				case ReportCitation.Event event -> {
					EventId current = events.get(event.id());
					if (current != null) {
						resolved.add(new ReportCitation.Event(current));
					}
				}
				case ReportCitation.Storyline storyline -> {
					StorylineId current = storylines.get(storyline.id());
					if (current != null) {
						resolved.add(new ReportCitation.Storyline(current));
					}
				}
			}
		}
		return List.copyOf(resolved);
	}

}
