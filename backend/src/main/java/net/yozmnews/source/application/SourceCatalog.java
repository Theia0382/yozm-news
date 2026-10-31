package net.yozmnews.source.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.yozmnews.source.FeedView;
import net.yozmnews.source.PresetView;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceView;
import net.yozmnews.source.UnknownSourceException;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PollIntervalSettings;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.registry.PollTarget;
import net.yozmnews.source.domain.registry.ProfileResolution;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.SourceListingRepository;
import net.yozmnews.source.domain.registry.SourceListingRepository.FeedRow;
import net.yozmnews.source.domain.registry.SourceListingRepository.SourceRow;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영자 목록 — 프리셋 · 소스 · 피드의 운영 설정과 그 프로필에서 얻은 정보. docs/specs/source.md §4.
 * <p>
 * 행은 읽기 트랜잭션 하나에서 읽고, 소스마다 지금의 프로필을 얻어 가리킬 곳을 잃었는지와 실제 주기를 판정한다(source-R8 · source-R14).
 */
@Component
class SourceCatalog {

	private final PresetCatalog catalog;

	private final SourceListingRepository listing;

	private final SourceRepository sources;

	private final ProfileResolver resolver;

	private final TransactionTemplate readOnly;

	SourceCatalog(PresetCatalog catalog, SourceListingRepository listing, SourceRepository sources,
			ProfileResolver resolver, PlatformTransactionManager transactions) {
		this.catalog = catalog;
		this.listing = listing;
		this.sources = sources;
		this.resolver = resolver;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
	}

	List<PresetView> presets() {
		Map<String, SourceId> registered = this.readOnly.execute(status -> this.listing.findAllSources()
			.stream()
			.filter(row -> row.presetKey() != null)
			.collect(Collectors.toMap(SourceRow::presetKey, SourceRow::id)));
		return this.catalog.all()
			.stream()
			.sorted(Comparator.comparing(preset -> preset.key().value()))
			.map(preset -> presetView(preset, registered.get(preset.key().value())))
			.toList();
	}

	List<SourceView> sources() {
		return this.readOnly.execute(status -> {
			Map<SourceId, List<FeedRow>> feeds = this.listing.findAllFeeds()
				.stream()
				.collect(Collectors.groupingBy(FeedRow::sourceId));
			return this.listing.findAllSources()
				.stream()
				.map(row -> sourceView(row, feeds.getOrDefault(row.id(), List.of())))
				.toList();
		});
	}

	/** @throws UnknownSourceException 없는 소스일 때 */
	Optional<String> operatorProfileText(SourceId id) {
		return this.sources.findById(id.value())
			.orElseThrow(() -> new UnknownSourceException(id))
			.operatorProfile();
	}

	private static PresetView presetView(Preset preset, @Nullable SourceId sourceId) {
		CollectionProfile profile = preset.profile();
		return new PresetView(preset.key().value(), profile.source().name(), profile.source().homepage(),
				preset.termsReviewedAt(), profile.feeds().size(), sourceId);
	}

	private SourceView sourceView(SourceRow row, List<FeedRow> feeds) {
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
		List<FeedView> feedViews = feeds.stream()
			.sorted(Comparator.comparingLong(feed -> feed.id().value()))
			.map(feed -> feedView(feed, resolution, row.pollIntervalMinutes()))
			.toList();
		return new SourceView(row.id(), row.presetKey(), row.enabled(), row.pollIntervalMinutes(), state,
				(profile != null) ? profile.source().name() : null,
				(profile != null) ? profile.source().homepage() : null,
				(profile != null) ? profile.termsReviewedAt().orElse(null) : null, feedViews);
	}

	private static FeedView feedView(FeedRow feed, ProfileResolution resolution,
			@Nullable Integer sourcePollIntervalMinutes) {
		return switch (PollTarget.of(resolution, new FeedKey(feed.feedKey()))) {
			case PollTarget.Ready ready -> {
				PollInterval effective = ready.interval(new PollIntervalSettings(interval(sourcePollIntervalMinutes),
						interval(feed.pollIntervalMinutes())));
				yield new FeedView(feed.id(), feed.feedKey(), feed.enabled(), feed.pollIntervalMinutes(), true,
						ready.feed().url(), ready.feed().section(), Math.toIntExact(effective.value().toMinutes()));
			}
			case PollTarget.Unavailable unavailable -> new FeedView(feed.id(), feed.feedKey(), feed.enabled(),
					feed.pollIntervalMinutes(), false, null, null, null);
		};
	}

	private static @Nullable PollInterval interval(@Nullable Integer minutes) {
		return (minutes != null) ? PollInterval.ofMinutes(minutes).orElseThrow() : null;
	}

}
