package net.yozmnews.source.domain.profile;

import java.time.Duration;
import java.util.Objects;

/**
 * 피드의 실제 수집 주기 — docs/specs/source.md §4 · source-R14
 */
public final class PollIntervalPolicy {

	/** 어디에도 값이 없을 때의 주기 — 운영 데이터로 다시 판단한다 */
	public static final PollInterval DEFAULT = new PollInterval(Duration.ofMinutes(15));

	private PollIntervalPolicy() {
	}

	/**
	 * 실제 주기를 정한다.
	 * @throws IllegalArgumentException 피드가 그 프로필의 피드가 아닐 때
	 */
	public static PollInterval effective(CollectionProfile profile, FeedSpec feed, PollIntervalSettings operator) {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(feed, "feed");
		Objects.requireNonNull(operator, "operator");
		if (!profile.feeds().contains(feed)) {
			throw new IllegalArgumentException("프로필에 없는 피드: " + feed.key());
		}
		if (operator.feed() != null) {
			return operator.feed();
		}
		if (operator.source() != null) {
			return operator.source();
		}
		if (feed.pollInterval() != null) {
			return feed.pollInterval();
		}
		return profile.pollInterval().orElse(DEFAULT);
	}

}
