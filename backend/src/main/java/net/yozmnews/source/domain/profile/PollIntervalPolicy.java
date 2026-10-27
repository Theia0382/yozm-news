package net.yozmnews.source.domain.profile;

import java.time.Duration;
import java.util.Objects;

/**
 * 피드의 실제 수집 주기를 정한다. 프로필의 주기는 권장 기본값이고 운영자 설정이 이긴다 — 운영자 피드 설정 → 운영자 소스 설정 →
 * 프로필 피드 권장값 → 프로필 소스 권장값 → {@link #DEFAULT} 순으로 처음 있는 값이다.
 */
public final class PollIntervalPolicy {

	/** 어디에도 값이 없을 때의 주기. 운영 데이터로 다시 판단한다 */
	public static final PollInterval DEFAULT = new PollInterval(Duration.ofMinutes(15));

	private PollIntervalPolicy() {
	}

	/**
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
