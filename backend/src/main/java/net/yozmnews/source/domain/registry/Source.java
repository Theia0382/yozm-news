package net.yozmnews.source.domain.registry;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PresetKey;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 소스(언론사) aggregate — docs/specs/source.md §4 · source-R1 · source-R7
 */
@Table("sources")
public final class Source {

	@Id
	private @Nullable Long id;

	private final @Nullable String presetKey;

	private final @Nullable String profile;

	private boolean enabled;

	private @Nullable Integer pollIntervalMinutes;

	@Version
	private @Nullable Long version;

	private final Instant createdAt;

	@PersistenceCreator
	Source(@Nullable Long id, @Nullable String presetKey, @Nullable String profile, boolean enabled,
			@Nullable Integer pollIntervalMinutes, @Nullable Long version, Instant createdAt) {
		if ((presetKey == null) == (profile == null)) {
			throw new IllegalArgumentException("소스는 프리셋 키와 운영자 프로필 가운데 하나만 갖는다");
		}
		this.id = id;
		this.presetKey = presetKey;
		this.profile = profile;
		this.enabled = enabled;
		this.pollIntervalMinutes = pollIntervalMinutes;
		this.version = version;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 프리셋을 참조하는 꺼진 소스를 만든다 */
	public static Source ofPreset(PresetKey key, Instant now) {
		return new Source(null, key.value(), null, false, null, null, now);
	}

	/** 운영자 프로필 원문을 가진 꺼진 소스를 만든다 — 원문 검증은 등록 유스케이스가 먼저 한다 */
	public static Source ofOperatorProfile(String text, Instant now) {
		return new Source(null, null, Objects.requireNonNull(text, "text"), false, null, null, now);
	}

	/**
	 * 소스 식별자를 돌려준다.
	 * @throws IllegalStateException 아직 저장하지 않았을 때
	 */
	public SourceId id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 소스다");
		}
		return new SourceId(this.id);
	}

	public Optional<PresetKey> presetKey() {
		return Optional.ofNullable(this.presetKey).map(PresetKey::new);
	}

	/**
	 * 운영자 프로필 원문을 돌려준다.
	 * @return 원문 — 프리셋을 참조하면 비어 있다
	 */
	public Optional<String> operatorProfile() {
		return Optional.ofNullable(this.profile);
	}

	public boolean enabled() {
		return this.enabled;
	}

	/**
	 * 운영자 소스 주기 설정을 돌려준다.
	 * @return 정하지 않았으면 null
	 */
	public @Nullable PollInterval pollInterval() {
		return (this.pollIntervalMinutes != null) ? PollInterval.ofMinutes(this.pollIntervalMinutes).orElseThrow()
				: null;
	}

	/**
	 * 소스를 켠다 — source-R4.
	 * @param resolution 이 소스의 프로필을 얻은 결과
	 */
	public Activation enable(ProfileResolution resolution) {
		return switch (resolution) {
			case ProfileResolution.Resolved resolved -> {
				if (resolved.profile().termsReviewedAt().isEmpty()) {
					yield new Activation.Refused(ActivationRefusal.TERMS_NOT_REVIEWED);
				}
				this.enabled = true;
				yield new Activation.Activated();
			}
			case ProfileResolution.PresetMissing missing -> new Activation.Refused(ActivationRefusal.PROFILE_UNAVAILABLE);
			case ProfileResolution.Invalid invalid -> new Activation.Refused(ActivationRefusal.PROFILE_UNAVAILABLE);
		};
	}

	public void disable() {
		this.enabled = false;
	}

	/**
	 * 운영자 소스 주기 설정을 바꾼다.
	 * @param interval 새 설정 — null 이면 설정을 지운다
	 */
	public void changePollInterval(@Nullable PollInterval interval) {
		this.pollIntervalMinutes = (interval != null) ? Math.toIntExact(interval.value().toMinutes()) : null;
	}

}
