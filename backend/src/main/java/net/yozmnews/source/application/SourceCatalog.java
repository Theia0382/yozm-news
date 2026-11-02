package net.yozmnews.source.application;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.source.FeedHealthView;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedView;
import net.yozmnews.source.LastFailureView;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceView;
import net.yozmnews.source.UnknownSourceException;
import net.yozmnews.source.domain.attribution.SourceAttributionRepository;
import net.yozmnews.source.domain.poll.FeedPollState;
import net.yozmnews.source.domain.poll.LastError;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PollIntervalSettings;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.registry.PollTarget;
import net.yozmnews.source.domain.registry.ProfileResolution;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceListingRepository;
import net.yozmnews.source.domain.registry.SourceListingRepository.FeedRow;
import net.yozmnews.source.domain.registry.SourceListingRepository.PollRow;
import net.yozmnews.source.domain.registry.SourceListingRepository.SourceRow;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영자 목록 — 프리셋 · 소스 · 피드의 운영 설정, 그 프로필에서 얻은 정보, 피드의 수집 상태. docs/specs/source.md §4.
 * <p>
 * 행은 읽기 트랜잭션 하나에서 읽고, 소스마다 지금의 프로필을 얻어 가리킬 곳을 잃었는지와 실제 주기를 판정한다(source-R8 · source-R14).
 * 공개 조회 {@code FeedLabels} 의 피드별 출처 이름 · 섹션도 같은 해석으로 낸다.
 */
@Component
class SourceCatalog {

	/** 숨긴 소스의 순서 — 숨긴 시각, 같으면 소스 ID */
	private static final Comparator<SourceRow> HIDDEN_ORDER = Comparator
		.comparing((SourceRow row) -> Objects.requireNonNull(row.hiddenAt(), "hiddenAt"))
		.thenComparingLong(row -> row.id().value());

	private final PresetCatalog catalog;

	private final SourceListingRepository listing;

	private final SourceRepository sources;

	private final ProfileResolver resolver;

	private final SourceAttributionRepository attributions;

	private final TransactionTemplate readOnly;

	SourceCatalog(PresetCatalog catalog, SourceListingRepository listing, SourceRepository sources,
			ProfileResolver resolver, SourceAttributionRepository attributions, PlatformTransactionManager transactions) {
		this.catalog = catalog;
		this.listing = listing;
		this.sources = sources;
		this.resolver = resolver;
		this.attributions = attributions;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
	}

	/** 프리셋 — 참조하는 소스(숨긴 소스 포함)와 그 소스를 숨겼는지를 함께 낸다 */
	List<PresetView> presets() {
		Map<String, SourceRow> registered = this.readOnly.execute(status -> this.listing.findAllSources()
			.stream()
			.filter(row -> row.presetKey() != null)
			.collect(Collectors.toMap(SourceRow::presetKey, row -> row)));
		return this.catalog.all()
			.stream()
			.sorted(Comparator.comparing(preset -> preset.key().value()))
			.map(preset -> presetView(preset, registered.get(preset.key().value())))
			.toList();
	}

	/** 숨기지 않은 소스 — 소스 ID 순 */
	List<SourceView> sources() {
		return list(false);
	}

	/** 숨긴 소스 — 숨긴 시각 순, 같으면 소스 ID 순 */
	List<SourceView> hiddenSources() {
		return list(true);
	}

	/** @throws UnknownSourceException 없는 소스 · 숨긴 소스일 때 */
	Optional<String> operatorProfileText(SourceId id) {
		Source source = this.sources.findById(id.value())
			.filter(found -> !found.hidden())
			.orElseThrow(() -> new UnknownSourceException(id));
		return source.operatorProfile();
	}

	/**
	 * 피드의 출처 이름 · 섹션 — 숨긴 소스 · 피드도 낸다. 운영 목록과 같은 해석이다.
	 * @return 피드 ID → 이름 · 섹션 — 없는 피드는 빠진다
	 */
	Map<FeedId, FeedLabel> labels(Collection<FeedId> feedIds) {
		if (feedIds.isEmpty()) {
			return Map.of();
		}
		Set<FeedId> wanted = Set.copyOf(feedIds);
		return this.readOnly.execute(status -> {
			Map<SourceId, List<FeedRow>> feeds = this.listing.findAllFeeds()
				.stream()
				.filter(feed -> wanted.contains(feed.id()))
				.collect(Collectors.groupingBy(FeedRow::sourceId));
			Map<SourceId, String> recordedNames = this.attributions.findAll();
			Map<FeedId, FeedLabel> labels = new HashMap<>();
			for (SourceRow row : this.listing.findAllSources()) {
				List<FeedRow> sourceFeeds = feeds.getOrDefault(row.id(), List.of());
				if (sourceFeeds.isEmpty()) {
					continue;
				}
				ProfileResolution resolution = this.resolver.resolve(row);
				String name = name(resolution, recordedNames.get(row.id()));
				for (FeedRow feed : sourceFeeds) {
					labels.put(feed.id(), new FeedLabel(name, section(resolution, feed)));
				}
			}
			return Map.copyOf(labels);
		});
	}

	private List<SourceView> list(boolean hidden) {
		return this.readOnly.execute(status -> {
			Map<SourceId, List<FeedRow>> feeds = this.listing.findAllFeeds()
				.stream()
				.collect(Collectors.groupingBy(FeedRow::sourceId));
			Map<SourceId, String> recordedNames = this.attributions.findAll();
			return this.listing.findAllSources()
				.stream()
				.filter(row -> (row.hiddenAt() != null) == hidden)
				.sorted(hidden ? HIDDEN_ORDER : Comparator.comparingLong(row -> row.id().value()))
				.map(row -> sourceView(row, feeds.getOrDefault(row.id(), List.of()), recordedNames.get(row.id())))
				.toList();
		});
	}

	private static PresetView presetView(Preset preset, @Nullable SourceRow source) {
		CollectionProfile profile = preset.profile();
		return new PresetView(preset.key().value(), profile.source().name(), profile.source().homepage(),
				preset.termsReviewedAt(), profile.feeds().size(), (source != null) ? source.id() : null,
				source != null && source.hiddenAt() != null);
	}

	private SourceView sourceView(SourceRow row, List<FeedRow> feeds, @Nullable String recordedName) {
		ProfileResolution resolution = this.resolver.resolve(row);
		@Nullable CollectionProfile profile = switch (resolution) {
			case ProfileResolution.Resolved resolved -> resolved.profile();
			case ProfileResolution.PresetMissing missing -> null;
			case ProfileResolution.Invalid invalid -> null;
		};
		ProfileState state = switch (resolution) {
			case ProfileResolution.Resolved resolved -> ProfileState.AVAILABLE;
			case ProfileResolution.PresetMissing missing -> ProfileState.PRESET_MISSING;
			case ProfileResolution.Invalid invalid -> ProfileState.INVALID;
		};
		List<FeedView> visible = feedViews(feeds, resolution, row.pollIntervalMinutes(), false);
		List<FeedView> hidden = feedViews(feeds, resolution, row.pollIntervalMinutes(), true);
		return new SourceView(row.id(), row.presetKey(), row.enabled(), row.pollIntervalMinutes(), state,
				name(resolution, recordedName),
				(profile != null) ? profile.source().homepage() : null,
				(profile != null) ? profile.termsReviewedAt().orElse(null) : null, visible, hidden);
	}

	/**
	 * 출처 이름 — 프로필의 소스 이름, 프로필을 얻지 못하면 기록된 출처 이름(없으면 null). 운영 목록과 공개 조회가 함께 쓰는 정본이다
	 */
	private static @Nullable String name(ProfileResolution resolution, @Nullable String recordedName) {
		return switch (resolution) {
			case ProfileResolution.Resolved resolved -> resolved.profile().source().name();
			case ProfileResolution.PresetMissing missing -> recordedName;
			case ProfileResolution.Invalid invalid -> recordedName;
		};
	}

	/** 피드 섹션 — 프로필에 그 피드가 있을 때만(없으면 null) */
	private static @Nullable String section(ProfileResolution resolution, FeedRow feed) {
		return switch (PollTarget.of(resolution, new FeedKey(feed.feedKey()))) {
			case PollTarget.Ready ready -> ready.feed().section();
			case PollTarget.Unavailable unavailable -> null;
		};
	}

	/** 숨김 여부가 같은 피드 — 피드 ID 순 */
	private static List<FeedView> feedViews(List<FeedRow> feeds, ProfileResolution resolution,
			@Nullable Integer sourcePollIntervalMinutes, boolean hidden) {
		return feeds.stream()
			.filter(feed -> (feed.hiddenAt() != null) == hidden)
			.sorted(Comparator.comparingLong(feed -> feed.id().value()))
			.map(feed -> feedView(feed, resolution, sourcePollIntervalMinutes))
			.toList();
	}

	private static FeedView feedView(FeedRow feed, ProfileResolution resolution,
			@Nullable Integer sourcePollIntervalMinutes) {
		return switch (PollTarget.of(resolution, new FeedKey(feed.feedKey()))) {
			case PollTarget.Ready ready -> {
				PollInterval effective = ready.interval(new PollIntervalSettings(interval(sourcePollIntervalMinutes),
						interval(feed.pollIntervalMinutes())));
				yield new FeedView(feed.id(), feed.feedKey(), feed.enabled(), feed.pollIntervalMinutes(), true,
						ready.feed().url(), section(resolution, feed), Math.toIntExact(effective.value().toMinutes()),
						health(feed));
			}
			case PollTarget.Unavailable unavailable -> new FeedView(feed.id(), feed.feedKey(), feed.enabled(),
					feed.pollIntervalMinutes(), false, null, null, null, health(feed));
		};
	}

	/** 수집 상태 — 행이 없으면 등록 · 기동 대조의 불변식(source-R3)이 깨진 것이라 조용히 넘기지 않는다 */
	private static FeedHealthView health(FeedRow feed) {
		PollRow poll = feed.poll();
		if (poll == null) {
			throw new IllegalStateException("피드에 수집 상태 행이 없다: " + feed.id().value());
		}
		FeedPollState state = poll.state();
		LastError error = state.lastError();
		LastFailureView lastFailure = (error != null) ? new LastFailureView(error.reason(),
				error.reason().permanence(), error.message(), error.httpStatus(), error.at()) : null;
		return new FeedHealthView(poll.status(), poll.statusChangedAt(), state.lastSuccessAt(),
				state.consecutiveFailures(), state.consecutivePermanentFailures(), lastFailure, state.lastNewItemAt(),
				state.latestPublishedAt());
	}

	private static @Nullable PollInterval interval(@Nullable Integer minutes) {
		return (minutes != null) ? PollInterval.ofMinutes(minutes).orElseThrow() : null;
	}

}
