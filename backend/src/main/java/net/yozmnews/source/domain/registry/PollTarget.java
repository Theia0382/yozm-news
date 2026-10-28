package net.yozmnews.source.domain.registry;

import java.util.Objects;

import net.yozmnews.source.domain.fetch.FeedDescriptor;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.FeedSpec;
import net.yozmnews.source.domain.profile.PollInterval;
import net.yozmnews.source.domain.profile.PollIntervalPolicy;
import net.yozmnews.source.domain.profile.PollIntervalSettings;

/**
 * 피드를 지금 수집할 수 있는가 — 프로필을 얻었고 그 프로필에 피드가 있어야 한다. 실제 주기는 피드가 프로필에 있음을 본 뒤에만 정한다
 * ({@link PollIntervalPolicy#effective} 는 프로필에 없는 피드에서 예외를 던진다). 이용조건 검토일은 소스를 켤 때 보는 소스 단위의
 * 조건이라 여기서 보지 않는다.
 */
public sealed interface PollTarget {

	static PollTarget of(ProfileResolution resolution, FeedKey key) {
		Objects.requireNonNull(key, "key");
		return switch (resolution) {
			case ProfileResolution.Resolved resolved -> resolved.profile()
				.feed(key)
				.<PollTarget>map(feed -> new Ready(resolved.profile(), feed))
				.orElseGet(() -> new Unavailable(ActivationRefusal.FEED_NOT_IN_PROFILE));
			case ProfileResolution.PresetMissing missing -> new Unavailable(ActivationRefusal.PROFILE_UNAVAILABLE);
			case ProfileResolution.Invalid invalid -> new Unavailable(ActivationRefusal.PROFILE_UNAVAILABLE);
		};
	}

	/**
	 * 수집할 수 있다.
	 *
	 * @param profile 피드를 읽는 프로필
	 * @param feed 그 프로필의 피드
	 */
	record Ready(CollectionProfile profile, FeedSpec feed) implements PollTarget {

		public Ready {
			Objects.requireNonNull(profile, "profile");
			Objects.requireNonNull(feed, "feed");
		}

		/** 실제 주기 — 정본은 {@link PollIntervalPolicy#effective} */
		public PollInterval interval(PollIntervalSettings operator) {
			return PollIntervalPolicy.effective(this.profile, this.feed, operator);
		}

		public FeedDescriptor descriptor() {
			return new FeedDescriptor(this.feed.url(), this.profile);
		}

	}

	/** 수집할 수 없다 */
	record Unavailable(ActivationRefusal reason) implements PollTarget {

		public Unavailable {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
