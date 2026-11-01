package net.yozmnews.source.domain.registry;

import java.util.Optional;

import net.yozmnews.source.FeedRemovalRefusal;
import net.yozmnews.source.domain.profile.FeedKey;

/**
 * 피드 숨기기 · 삭제의 대상 판정 — docs/specs/source.md §5.
 * <p>
 * 대상은 프로필을 얻었고 그 프로필에 없는 피드다. 프로필에 있는 피드는 숨기거나 지워도 원문 수정 · 기동 대조가 다시 더하고, 프로필을
 * 얻지 못한 소스는 소스 단위로 숨기거나 지운다.
 */
public final class FeedRemovalRule {

	private FeedRemovalRule() {
	}

	/**
	 * 숨기거나 지우지 않을 이유를 정한다.
	 * @return 대상이면 비어 있다
	 */
	public static Optional<FeedRemovalRefusal> refusal(ProfileResolution resolution, FeedKey key) {
		return switch (PollTarget.of(resolution, key)) {
			case PollTarget.Ready ready -> Optional.of(FeedRemovalRefusal.IN_PROFILE);
			case PollTarget.Unavailable unavailable -> switch (unavailable.reason()) {
				case FEED_NOT_IN_PROFILE -> Optional.empty();
				case PROFILE_UNAVAILABLE -> Optional.of(FeedRemovalRefusal.PROFILE_UNAVAILABLE);
				case TERMS_NOT_REVIEWED -> throw new IllegalStateException("피드의 수집 가능 판정은 검토일을 보지 않는다");
			};
		};
	}

}
