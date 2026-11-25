package net.yozmnews.report.application;

import java.time.ZoneId;
import java.time.ZoneOffset;
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
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportView;
import net.yozmnews.report.domain.material.ReportSnapshot.SnapshotStats;
import net.yozmnews.report.domain.view.ReportRow;
import net.yozmnews.report.domain.view.ReportViewRepository;
import net.yozmnews.report.domain.view.ReportViews;
import net.yozmnews.report.domain.view.StoredSection;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserTimezones;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 조회 공개 조회의 구현 · docs/specs/report.md §4 · report-R11 · report-R12 · report-R25 · report-R26
 * <p>
 * 행 · 섹션 · 인용은 읽기 전용 트랜잭션 하나에서 읽고, 인용의 사건 · 스토리라인과 스냅숏 사용자 스토리라인 줄의 헤드는 트랜잭션 밖에서 {@code event} ·
 * {@code storyline} 의 공개 조회로 지금 것으로 푼다 — 스토리라인은 인용과 헤드를 한 번에 묻는다. 생성 때 이은 직전 리포트의 행도 같은
 * 트랜잭션에서 읽는다. 시작 전 행의 기간 날짜는 {@code user} 의 {@link UserTimezones} 로 지금 사용자 시간대를 읽어 적는다. 판단은
 * {@link ReportViews} 가 한다. 쓰지 않는다.
 */
@Service
class ReportDirectoryService implements ReportDirectory {

	private final ReportViewRepository views;

	private final EventDirectory events;

	private final StorylineDirectory storylines;

	private final UserTimezones timezones;

	private final TransactionTemplate readOnly;

	ReportDirectoryService(ReportViewRepository views, EventDirectory events, StorylineDirectory storylines,
			UserTimezones timezones, PlatformTransactionManager transactions) {
		this.views = views;
		this.events = events;
		this.storylines = storylines;
		this.timezones = timezones;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
	}

	@Override
	public ReportPage list(UserId user, ReportPeriodKind kind, @Nullable ReportId before, int size) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(kind, "kind");
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE_SIZE + " 이다: " + size);
		}
		List<ReportRow> rows = Objects
			.requireNonNull(this.readOnly.execute(status -> this.views.page(user, kind, before, size + 1)));
		return ReportViews.page(rows, size, fallbackZone(user, rows));
	}

	@Override
	public Optional<ReportView> find(UserId user, ReportId id) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(id, "id");
		Read read = Objects.requireNonNull(this.readOnly.execute(status -> {
			Optional<ReportRow> row = this.views.find(user, id);
			List<StoredSection> sections = row.filter(found -> found.status() == ReportStatus.READY)
				.map(found -> this.views.sections(user, id))
				.orElse(List.of());
			ReportRow previous = row.map(ReportRow::previous)
				.flatMap(previousId -> this.views.find(user, previousId))
				.orElse(null);
			return new Read(row, sections, previous);
		}));
		if (read.row().isEmpty()) {
			return Optional.empty();
		}
		ReportRow row = read.row().get();
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
		SnapshotStats snapshot = ReportViews.snapshotOf(row);
		storylineIds.addAll(ReportViews.snapshotHeads(snapshot));
		List<ReportRow> dated = (read.previous() == null) ? List.of(row) : List.of(row, read.previous());
		return Optional.of(ReportViews.view(row, read.sections(), snapshot, currentEvents(eventIds),
				currentStorylines(storylineIds), read.previous(), fallbackZone(user, dated)));
	}

	/**
	 * 시작 전 행(시간대가 없다)의 날짜를 적을 지금 사용자 시간대 — 그런 행이 있을 때만 묻는다. 계정이 없으면 UTC(화면 경로의 세션 사용자는 계정이
	 * 있다)
	 */
	private ZoneId fallbackZone(UserId user, List<ReportRow> rows) {
		if (rows.stream().allMatch(row -> row.zone() != null)) {
			return ZoneOffset.UTC;
		}
		return this.timezones.timezoneOf(user).orElse(ZoneOffset.UTC);
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

	/** 스토리라인(인용 · 스냅숏 사용자 스토리라인 줄의 헤드)마다 지금 스토리라인 — 합쳐졌으면 남는 것. 지운 스토리라인은 없다 */
	private Map<StorylineId, StorylineId> currentStorylines(Set<StorylineId> ids) {
		Map<StorylineId, StorylineId> current = new HashMap<>();
		if (ids.isEmpty()) {
			return current;
		}
		this.storylines.findAll(ids).forEach((asked, view) -> current.put(asked, view.id()));
		return current;
	}

	private record Read(Optional<ReportRow> row, List<StoredSection> sections, @Nullable ReportRow previous) {
	}

}
