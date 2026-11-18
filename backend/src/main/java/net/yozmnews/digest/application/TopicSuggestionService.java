package net.yozmnews.digest.application;

import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import net.yozmnews.digest.domain.interest.InterestLevels;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.TopicInterestNow;
import net.yozmnews.user.UserTimezones;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 구독 제안의 관심 — {@code user} 가 선언한 {@link TopicInterestNow} 의 구현. 지금의 관심은 {@link CurrentInterests} 가 낸다.
 * docs/specs/digest.md §4
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class TopicSuggestionService implements TopicInterestNow {

	private final TagSetDirectory tagSets;

	private final UserTimezones timezones;

	private final CurrentInterests interests;

	private final InstantSource clock;

	@Autowired
	TopicSuggestionService(TagSetDirectory tagSets, UserTimezones timezones, CurrentInterests interests) {
		this(tagSets, timezones, interests, InstantSource.system());
	}

	/** 시험이 시계를 넣는다 */
	TopicSuggestionService(TagSetDirectory tagSets, UserTimezones timezones, CurrentInterests interests,
			InstantSource clock) {
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.timezones = Objects.requireNonNull(timezones, "timezones");
		this.interests = Objects.requireNonNull(interests, "interests");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public OptionalDouble levelOf(UserId user, TagSetId tagSet, String subtopic) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(tagSet, "tagSet");
		Objects.requireNonNull(subtopic, "subtopic");
		if (!this.tagSets.activeTagSet().id().equals(tagSet) || this.timezones.timezoneOf(user).isEmpty()) {
			return OptionalDouble.empty();
		}
		Optional<InterestLevels> levels = this.interests.levels(user, tagSet, this.clock.instant());
		return levels.isPresent() ? OptionalDouble.of(levels.get().level(subtopic)) : OptionalDouble.empty();
	}

}
