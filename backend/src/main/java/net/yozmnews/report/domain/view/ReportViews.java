package net.yozmnews.report.domain.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.yozmnews.event.EventId;
import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPage;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportSectionStats;
import net.yozmnews.report.ReportSectionView;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import net.yozmnews.report.ReportView;
import net.yozmnews.report.domain.material.ReportSnapshot;
import net.yozmnews.report.domain.material.ReportSnapshot.FlowLevelStat;
import net.yozmnews.report.domain.material.ReportSnapshot.FlowStat;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotStats;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;

/**
 * 리포트 조회의 풀기 · 숨기기 · 숫자 · 쪽 나누기 — 정본 · docs/specs/report.md §5 · report-R11 · report-R12 · report-R25
 * <p>
 * 저장한 인용을 풀기 결과(지금 사건 · 지금 스토리라인)로 바꾸고 결과에 없는 것(지운 것)은 뺀다. 인용이 하나 이상 있었는데 하나도 남지 않은 갈래 섹션은
 * 숨기고 그 수를 세며, 갈래 섹션이 하나 이상 있었고 모두 숨었으면 개요도 숨긴다. 보이는 갈래 섹션에는 스냅숏의 그 갈래 칸을 숫자로 붙이고, 흐름 줄의
 * 생성 때 헤드는 인용과 같은 스토리라인 풀기 결과로 바꾼다 — 지운 헤드의 줄은 빼고, 풀린 뒤 같은 스토리라인이 된 줄은 첫 줄만 둔다. 행을 고치지
 * 않는다.
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
	 * 스냅숏 흐름 줄의 생성 때 헤드 — 조회가 인용의 스토리라인과 함께 풀기에 넘긴다. {@code READY} 가 아니거나 스냅숏을 읽지 못하면 없다
	 */
	public static Set<StorylineId> snapshotHeads(ReportRow row) {
		Objects.requireNonNull(row, "row");
		if (row.summary().status() != ReportStatus.READY) {
			return Set.of();
		}
		Set<StorylineId> heads = new LinkedHashSet<>();
		ReportSnapshot.stats(row.statsSnapshot()).ifPresent(stats -> {
			if (stats.flows() != null) {
				stats.flows().flows().forEach(flow -> heads.add(flow.head()));
			}
			if (stats.interests() != null) {
				stats.interests().flows().forEach(flow -> heads.add(flow.head()));
			}
		});
		return heads;
	}

	/**
	 * 리포트 한 편 — {@code READY} 가 아니면 섹션이 없고 숨긴 수는 0 이다
	 * @param events 저장한 사건 → 지금 사건(지운 사건은 없다)
	 * @param storylines 저장한 스토리라인 · 스냅숏 흐름 줄의 헤드 → 지금 스토리라인(지운 스토리라인은 없다)
	 */
	public static ReportView view(ReportRow row, List<StoredSection> stored, Map<EventId, EventId> events,
			Map<StorylineId, StorylineId> storylines) {
		Objects.requireNonNull(row, "row");
		Objects.requireNonNull(stored, "stored");
		Objects.requireNonNull(events, "events");
		Objects.requireNonNull(storylines, "storylines");
		Visible visible = (row.summary().status() == ReportStatus.READY)
				? visible(stored, events, storylines, ReportSnapshot.stats(row.statsSnapshot()).orElse(null))
				: new Visible(List.of(), 0);
		return new ReportView(row.summary(), row.provider(), row.model(), row.inputTokens(), row.outputTokens(),
				visible.hidden(), visible.sections());
	}

	private static Visible visible(List<StoredSection> stored, Map<EventId, EventId> events,
			Map<StorylineId, StorylineId> storylines, @Nullable SnapshotStats snapshot) {
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
			shown.add(new ReportSectionView(section.kind(), section.title(), section.body(), resolved,
					(snapshot == null) ? null : stats(section.kind(), snapshot, storylines)));
		}
		boolean everyKindSectionHidden = kindSections > 0 && hidden == kindSections;
		if (overview != null && !everyKindSectionHidden) {
			shown.addFirst(new ReportSectionView(ReportSectionKind.OVERVIEW, null, overview.body(), List.of(), null));
		}
		return new Visible(List.copyOf(shown), hidden);
	}

	/** 갈래 섹션의 숫자 — 스냅숏에 그 갈래 칸이 없으면 없다 */
	private static @Nullable ReportSectionStats stats(ReportSectionKind kind, SnapshotStats snapshot,
			Map<StorylineId, StorylineId> storylines) {
		return switch (kind) {
			case OVERVIEW -> null;
			case STORYLINES -> (snapshot.flows() == null) ? null
					: new ReportSectionStats.Flows(resolveFlows(snapshot.flows().flows(), FlowStat::head, storylines,
							(flow, current) -> new ReportSectionStats.FlowCount(current, flow.articles(), flow.previous(),
									flow.trend(), flow.trackedInPeriod())));
			case COVERAGE -> (snapshot.coverage() == null) ? null
					: new ReportSectionStats.Coverage(snapshot.coverage()
						.topics()
						.stream()
						.map(topic -> new ReportSectionStats.TopicCount(topic.name(), topic.articles(), topic.previous(),
								topic.trend()))
						.toList());
			case INTERESTS -> (snapshot.interests() == null) ? null
					: new ReportSectionStats.Interests(snapshot.interests()
						.topics()
						.stream()
						.map(topic -> new ReportSectionStats.TopicLevel(topic.name(), topic.start(), topic.end(),
								topic.shift()))
						.toList(),
							resolveFlows(snapshot.interests().flows(), FlowLevelStat::head, storylines,
									(flow, current) -> new ReportSectionStats.FlowLevel(current, flow.start(), flow.end(),
											flow.shift())));
		};
	}

	/** 흐름 줄의 헤드를 지금 스토리라인으로 — 지운 헤드의 줄은 빼고, 풀린 뒤 같은 스토리라인은 첫 줄만 */
	private static <S, T> List<T> resolveFlows(List<S> lines, Function<S, StorylineId> head,
			Map<StorylineId, StorylineId> storylines, BiFunction<S, StorylineId, T> line) {
		Set<StorylineId> seen = new LinkedHashSet<>();
		List<T> resolved = new ArrayList<>();
		for (S stat : lines) {
			StorylineId current = storylines.get(head.apply(stat));
			if (current != null && seen.add(current)) {
				resolved.add(line.apply(stat, current));
			}
		}
		return List.copyOf(resolved);
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

	/** 보이는 섹션과 숨긴 갈래 섹션 수 */
	private record Visible(List<ReportSectionView> sections, int hidden) {
	}

}
