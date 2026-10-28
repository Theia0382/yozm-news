package net.yozmnews.source.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedSpec;
import net.yozmnews.source.domain.profile.Preset;
import net.yozmnews.source.domain.profile.PresetCatalog;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import net.yozmnews.source.domain.registry.Source;
import net.yozmnews.source.domain.registry.SourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소스를 등록한다 — 소스 행과 프로필의 피드마다 꺼진 피드 행 · 수집 상태 행을 한 트랜잭션에서 만든다. 켜기는 따로 한다.
 */
@Service
class SourceRegistration {

	private final PresetCatalog catalog;

	private final ProfileParser parser;

	private final SourceRepository sources;

	private final FeedProvisioner provisioner;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	@Autowired
	SourceRegistration(PresetCatalog catalog, ProfileParser parser, SourceRepository sources,
			FeedProvisioner provisioner, TransactionTemplate transaction) {
		this(catalog, parser, sources, provisioner, transaction, InstantSource.system());
	}

	SourceRegistration(PresetCatalog catalog, ProfileParser parser, SourceRepository sources,
			FeedProvisioner provisioner, TransactionTemplate transaction, InstantSource clock) {
		this.catalog = catalog;
		this.parser = parser;
		this.sources = sources;
		this.provisioner = provisioner;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 프리셋을 참조하는 소스. 같은 프리셋을 두 소스가 참조하지 않는다 — 판정은 {@code sources.preset_key} 의 고유 제약 하나로 한다.
	 * 제약 위반은 트랜잭션 밖에서 잡는다: 안에서 잡으면 이미 중단된 트랜잭션을 커밋하려다 실패한다.
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

	/** 운영자 프로필 소스 — 원문을 검증하고, 통과하면 원문 그대로 저장한다 */
	Registration registerOperatorProfile(String text) {
		return switch (this.parser.parseOperatorProfile(text)) {
			case ProfileParse.Parsed parsed -> register(Source.ofOperatorProfile(text, this.clock.instant()),
					parsed.profile());
			case ProfileParse.Invalid invalid -> new Registration.InvalidProfile(invalid.problems());
		};
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
