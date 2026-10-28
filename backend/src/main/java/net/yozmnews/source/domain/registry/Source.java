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
 * 소스(언론사) — 운영자가 쓰는 aggregate. 프리셋을 참조하거나 운영자 프로필 원문을 갖고(둘 중 하나), 그 밖에는 운영 설정(켜짐 ·
 * 수집 주기)만 둔다. 프리셋을 참조하면 프로필을 복사하지 않는다 — 프리셋은 저장소 파일이 정본이다.
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

	/** 프리셋을 참조하는 소스 — 꺼진 채로 */
	public static Source ofPreset(PresetKey key, Instant now) {
		return new Source(null, key.value(), null, false, null, null, now);
	}

	/** 운영자 프로필 소스 — 꺼진 채로. 원문 검증은 등록 유스케이스가 먼저 한다 */
	public static Source ofOperatorProfile(String text, Instant now) {
		return new Source(null, null, Objects.requireNonNull(text, "text"), false, null, null, now);
	}

	/** @throws IllegalStateException 아직 저장하지 않았을 때 */
	public SourceId id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 소스다");
		}
		return new SourceId(this.id);
	}

	public Optional<PresetKey> presetKey() {
		return Optional.ofNullable(this.presetKey).map(PresetKey::new);
	}

	/** 운영자 프로필 원문 — 프리셋을 참조하면 빈 값 */
	public Optional<String> operatorProfile() {
		return Optional.ofNullable(this.profile);
	}

	public boolean enabled() {
		return this.enabled;
	}

	/** 운영자 소스 주기 설정 — 정하지 않았으면 null */
	public @Nullable PollInterval pollInterval() {
		return (this.pollIntervalMinutes != null) ? PollInterval.ofMinutes(this.pollIntervalMinutes).orElseThrow()
				: null;
	}

	/**
	 * 켠다 — 프로필을 얻었고 그 프로필에 이용조건 검토일이 있어야 한다. 거절하면 상태를 바꾸지 않는다.
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

	/** @param interval 새 설정 — null 이면 설정을 지운다 */
	public void changePollInterval(@Nullable PollInterval interval) {
		this.pollIntervalMinutes = (interval != null) ? Math.toIntExact(interval.value().toMinutes()) : null;
	}

}
