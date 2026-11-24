package net.yozmnews.report.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportDirectory;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPage;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportView;
import net.yozmnews.report.domain.view.ReportRow;
import net.yozmnews.report.domain.view.ReportViewRepository;
import net.yozmnews.report.domain.view.ReportViews;
import net.yozmnews.report.domain.view.StoredSection;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 조회 공개 조회의 구현 · docs/specs/report.md §4 · report-R11 · report-R12 · report-R25
 * <p>
 * 행 · 섹션 · 인용은 읽기 전용 트랜잭션 하나에서 읽고, 인용의 사건 · 스토리라인과 스냅숏 흐름 줄의 헤드는 트랜잭션 밖에서 {@code event} ·
 * {@code storyline} 의 공개 조회로 지금 것으로 푼다 — 스토리라인은 인용과 헤드를 한 번에 묻는다. 판단은 {@link ReportViews} 가 한다. 쓰지 않는다.
 */
@Service
class ReportDirectoryService implements ReportDirectory {

	private final ReportViewRepository views;

	private final EventDirectory events;

	private final StorylineDirectory storylines;

	private final TransactionTemplate readOnly;

	ReportDirectoryService(ReportViewRepository views, EventDirectory events, StorylineDirectory storylines,
			PlatformTransactionManager transactions) {
		this.views = views;
		this.events = events;
		this.storylines = storylines;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
	}

	@Override
	public ReportPage list(UserId user, @Nullable ReportId before, int size) {
		Objects.requireNonNull(user, "user");
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE_SIZE + " 이다: " + size);
		}
		List<ReportRow> rows = Objects
			.requireNonNull(this.readOnly.execute(status -> this.views.page(user, before, size + 1)));
		return ReportViews.page(rows, size);
	}

	@Override
	public Optional<ReportView> find(UserId user, ReportId id) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(id, "id");
		Read read = Objects.requireNonNull(this.readOnly.execute(status -> {
			Optional<ReportRow> row = this.views.find(user, id);
			List<StoredSection> sections = row.filter(found -> found.summary().status() == ReportStatus.READY)
				.map(found -> this.views.sections(user, id))
				.orElse(List.of());
			return new Read(row, sections);
		}));
		if (read.row().isEmpty()) {
			return Optional.empty();
		}
		Set<EventId> eventIds = new LinkedHashSet<>();
		Set<StorylineId> storylineIds = new LinkedHashSet<>();
		for (StoredSection section : read.sections()) {
			for (ReportCitation citation : section.citations()) {
				switch (citation) {
					case ReportCitation.Event event -> eventIds.add(event.id());
					case ReportCitation.Storyline storyline -> storylineIds.add(storyline.id());
				}
			}
		}
		storylineIds.addAll(ReportViews.snapshotHeads(read.row().get()));
		return Optional.of(ReportViews.view(read.row().get(), read.sections(), currentEvents(eventIds),
				currentStorylines(storylineIds)));
	}

	/** 사건마다 지금 사건 — {@link EventDirectory#MAX_RESOLVE_EVENTS} 씩 묻는다. 지운 사건은 없다 */
	private Map<EventId, EventId> currentEvents(Set<EventId> ids) {
		Map<EventId, EventId> current = new HashMap<>();
		List<EventId> all = new ArrayList<>(ids);
		for (int from = 0; from < all.size(); from += EventDirectory.MAX_RESOLVE_EVENTS) {
			current.putAll(this.events
				.currentOf(all.subList(from, Math.min(all.size(), from + EventDirectory.MAX_RESOLVE_EVENTS))));
		}
		return current;
	}

	/** 스토리라인(인용 · 스냅숏 흐름 줄의 헤드)마다 지금 스토리라인 — 합쳐졌으면 남는 것. 지운 스토리라인은 없다 */
	private Map<StorylineId, StorylineId> currentStorylines(Set<StorylineId> ids) {
		Map<StorylineId, StorylineId> current = new HashMap<>();
		if (ids.isEmpty()) {
			return current;
		}
		this.storylines.findAll(ids).forEach((asked, view) -> current.put(asked, view.id()));
		return current;
	}

	private record Read(Optional<ReportRow> row, List<StoredSection> sections) {
	}

}
