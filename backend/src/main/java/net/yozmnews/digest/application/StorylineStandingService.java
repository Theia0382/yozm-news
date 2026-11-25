package net.yozmnews.digest.application;

import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.digest.application.CurrentInterests.StorylineInterestsNow;
import net.yozmnews.digest.domain.interest.StorylineLevels;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.StorylineStanding;
import net.yozmnews.user.StorylineStandingNow;
import net.yozmnews.user.UserTimezones;
import org.springframework.stereotype.Service;

/**
 * {@code user} 가 선언한 {@link StorylineStandingNow} 의 구현 — 지금의 스토리라인 관심({@link CurrentInterests})에서 관심 스토리라인과 남길
 * 스토리라인의 꼭대기를 낸다. 판정과 기준은 {@link StorylineLevels} 의 것 그대로다. docs/specs/digest.md §4
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다. 재료의 태그 체계 활성 버전이 어긋나면 처음부터 다시 한다.
 */
@Service
class StorylineStandingService implements StorylineStandingNow {

	private final TagSetDirectory tagSets;

	private final UserTimezones timezones;

	private final CurrentInterests interests;

	private final InstantSource clock;

	StorylineStandingService(TagSetDirectory tagSets, UserTimezones timezones, CurrentInterests interests,
			InstantSource clock) {
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.timezones = Objects.requireNonNull(timezones, "timezones");
		this.interests = Objects.requireNonNull(interests, "interests");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<StorylineStanding> standingOf(UserId user) {
		Objects.requireNonNull(user, "user");
		if (this.timezones.timezoneOf(user).isEmpty()) {
			return Optional.empty();
		}
		for (int attempt = 0; attempt < ActiveEvaluations.ATTEMPTS; attempt++) {
			Optional<StorylineInterestsNow> now = this.interests.storylines(user, this.tagSets.activeTagSet().id(),
					this.clock.instant());
			if (now.isPresent()) {
				StorylineLevels levels = now.get().levels();
				return Optional.of(new StorylineStanding(levels.followed(now.get().trackedTops()), levels.kept()));
			}
		}
		throw new IllegalStateException(
				"스토리라인 판정 재료의 태그 체계 활성 버전이 " + ActiveEvaluations.ATTEMPTS + " 번 어긋났다");
	}

}
