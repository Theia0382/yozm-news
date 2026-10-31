package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.UnknownSourceException;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.FeedSpec;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.FeedRepository;
import net.yozmnews.source.domain.registry.ProfileResolution;
import net.yozmnews.source.domain.registry.ProfileResolver;
import net.yozmnews.source.domain.registry.ProfileRevision;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소스 등록과 운영자 원문 수정 — docs/specs/source.md §4 · source-R1 ~ source-R3
 */
@Service
class SourceRegistration {

	private final PresetCatalog catalog;

	private final ProfileParser parser;

	private final SourceRepository sources;

	private final FeedRepository feeds;

	private final ProfileResolver resolver;

	private final FeedProvisioner provisioner;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SourceRegistration(PresetCatalog catalog, ProfileParser parser, SourceRepository sources, FeedRepository feeds,
			ProfileResolver resolver, FeedProvisioner provisioner, TransactionTemplate transaction) {
		this(catalog, parser, sources, feeds, resolver, provisioner, transaction, InstantSource.system());
	}

	SourceRegistration(PresetCatalog catalog, ProfileParser parser, SourceRepository sources, FeedRepository feeds,
			ProfileResolver resolver, FeedProvisioner provisioner, TransactionTemplate transaction,
			InstantSource clock) {
		this.catalog = catalog;
		this.parser = parser;
		this.sources = sources;
		this.feeds = feeds;
		this.resolver = resolver;
		this.provisioner = provisioner;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 프리셋을 참조하는 소스를 등록한다 — source-R2. 같은 프리셋의 판정은 {@code sources.preset_key} 의 고유 제약 하나로 하고,
	 * 제약 위반은 트랜잭션 밖에서 잡는다 — 안에서 잡으면 이미 중단된 트랜잭션을 커밋하려다 실패한다.
	 */
	Registration registerPreset(PresetKey key) {
		Optional<Preset> preset = this.catalog.find(key);
		if (preset.isEmpty()) {
			return new Registration.PresetNotFound(key);
		}
		try {
			return register(Source.ofPreset(key, this.clock.instant()), preset.get().profile());
		}
		catch (DuplicateKeyException ex) {
			return new Registration.PresetAlreadyRegistered(key);
		}
	}

	/** 운영자 프로필 소스를 등록한다 — source-R1 */
	Registration registerOperatorProfile(String text) {
		return switch (this.parser.parseOperatorProfile(text)) {
			case ProfileParse.Parsed parsed -> register(Source.ofOperatorProfile(text, this.clock.instant()),
					parsed.profile());
			case ProfileParse.Invalid invalid -> new Registration.InvalidProfile(invalid.problems());
		};
	}

	/**
	 * 운영자 원문을 고친다 — 한 트랜잭션. 읽기 설정이 바뀐 켜진 피드와, 검토일이 사라진 켜진 소스를 끄고, 원문에 새로 생긴 피드를 꺼진 채
	 * 더한다. 그 소스의 피드 행은 바뀌지 않았어도 모두 저장한다 — 피드 켜기의 저장 직전 판정과 겹치면 늦은 쪽이 낙관적 잠금으로 실패하게
	 * 한다(docs/specs/source.md §5 · 운영자 원문 수정).
	 * @throws UnknownSourceException 없는 소스일 때 — 원문을 검증하기 전에 가른다
	 */
	Revision reviseOperatorProfile(SourceId id, String text) {
		Instant now = this.clock.instant();
		return this.transaction.execute(status -> {
			Source source = this.sources.findById(id.value()).orElseThrow(() -> new UnknownSourceException(id));
			if (source.operatorProfile().isEmpty()) {
				return new Revision.NotOperatorProfile();
			}
			CollectionProfile after;
			switch (this.parser.parseOperatorProfile(text)) {
				case ProfileParse.Invalid invalid -> {
					return new Revision.InvalidProfile(invalid.problems());
				}
				case ProfileParse.Parsed parsed -> after = parsed.profile();
			}
			ProfileResolution before = this.resolver.resolve(source);
			source.reviseOperatorProfile(text);
			boolean sourceDisabled = source.enabled() && !ProfileRevision.keepsSourceEnabled(after);
			if (sourceDisabled) {
				source.disable();
			}
			this.sources.save(source);
			List<Feed> existing = this.feeds.findAllBySourceId(id.value());
			List<FeedId> disabled = new ArrayList<>();
			for (Feed feed : existing.stream().sorted(Comparator.comparingLong(feed -> feed.id().value())).toList()) {
				if (feed.enabled() && !ProfileRevision.keepsEnabled(before, after, feed.feedKey())) {
					feed.disable();
					disabled.add(feed.id());
				}
				this.feeds.save(feed);
			}
			Set<FeedKey> known = existing.stream().map(Feed::feedKey).collect(Collectors.toSet());
			List<FeedId> added = new ArrayList<>();
			for (FeedSpec spec : after.feeds()) {
				if (!known.contains(spec.key())) {
					added.add(this.provisioner.provision(id, spec.key(), now).id());
				}
			}
			return new Revision.Revised(disabled, sourceDisabled, added);
		});
	}

	private Registration register(Source source, CollectionProfile profile) {
		Instant now = this.clock.instant();
		return this.transaction.execute(status -> {
			Source saved = this.sources.save(source);
			for (FeedSpec feed : profile.feeds()) {
				this.provisioner.provision(saved.id(), feed.key(), now);
			}
			return new Registration.Registered(saved.id());
		});
	}

}
