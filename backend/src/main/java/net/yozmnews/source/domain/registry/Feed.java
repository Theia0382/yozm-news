package net.yozmnews.source.domain.registry;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.PollInterval;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 피드 aggregate — docs/specs/source.md §4 · source-R1 · source-R7
 */
@Table("feeds")
public final class Feed {

	@Id
	private Long id;

	private final long sourceId;

	private final String feedKey;

	private boolean enabled;

	private @Nullable Integer pollIntervalMinutes;

	@Version
	private Long version;

	private final Instant createdAt;

	@PersistenceCreator
	Feed(Long id, long sourceId, String feedKey, boolean enabled, @Nullable Integer pollIntervalMinutes, Long version,
			Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.sourceId = sourceId;
		this.feedKey = Objects.requireNonNull(feedKey, "feedKey");
		this.enabled = enabled;
		this.pollIntervalMinutes = pollIntervalMinutes;
		this.version = Objects.requireNonNull(version, "version");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public FeedId id() {
		return new FeedId(this.id);
	}

	public SourceId sourceId() {
		return new SourceId(this.sourceId);
	}

	public FeedKey feedKey() {
		return new FeedKey(this.feedKey);
	}

	public boolean enabled() {
		return this.enabled;
	}

	/**
	 * 운영자 피드 주기 설정을 돌려준다.
	 * @return 정하지 않았으면 null
	 */
	public @Nullable PollInterval pollInterval() {
		return (this.pollIntervalMinutes != null) ? PollInterval.ofMinutes(this.pollIntervalMinutes).orElseThrow()
				: null;
	}

	/**
	 * 피드를 켠다 — source-R5. 판정은 {@link PollTarget#of} 가 한다.
	 * @param resolution 이 피드가 속한 소스의 프로필을 얻은 결과
	 */
	public Activation enable(ProfileResolution resolution) {
		return switch (PollTarget.of(resolution, feedKey())) {
			case PollTarget.Ready ready -> {
				this.enabled = true;
				yield new Activation.Activated();
			}
			case PollTarget.Unavailable unavailable -> new Activation.Refused(unavailable.reason());
		};
	}

	public void disable() {
		this.enabled = false;
	}

	/**
	 * 운영자 피드 주기 설정을 바꾼다.
	 * @param interval 새 설정 — null 이면 설정을 지운다
	 */
	public void changePollInterval(@Nullable PollInterval interval) {
		this.pollIntervalMinutes = (interval != null) ? Math.toIntExact(interval.value().toMinutes()) : null;
	}

}
