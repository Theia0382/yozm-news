package net.yozmnews.digest.application;

import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import net.yozmnews.digest.TopicSuggestionList;
import net.yozmnews.digest.TopicSuggestions;
import net.yozmnews.digest.domain.interest.DeclinedTopic;
import net.yozmnews.digest.domain.interest.InterestLevels;
import net.yozmnews.digest.domain.interest.TopicSuggestionRule;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.TopicInterestNow;
import net.yozmnews.user.TopicSubscription;
import net.yozmnews.user.TopicSuggestionDecline;
import net.yozmnews.user.TopicSuggestionDeclineList;
import net.yozmnews.user.TopicSuggestionDeclines;
import net.yozmnews.user.UserTimezones;
import org.springframework.stereotype.Service;

/**
 * 구독 제안 — {@link TopicSuggestions} 와 {@code user} 가 선언한 {@link TopicInterestNow} 의 구현. 지금의 관심은 둘 다
 * {@link CurrentInterests} 가 내고, 제안 판정은 {@link TopicSuggestionRule} 이 한다. 구독한 중분류는 {@code user} 의
 * {@link DigestInterestsDirectory}, 거절은 {@link TopicSuggestionDeclines} 로 읽는다. docs/specs/digest.md §4 · digest-R41 ·
 * digest-R42
 * <p>
 * 트랜잭션 없이 조회를 차례로 부른다. 제안 조회는 관심 설정의 키 버전 · 거절을 푼 버전 · 관심도 재료의 키 버전이 다르면(조회 사이의 활성 전환)
 * 처음부터 다시 하고 {@link ActiveEvaluations#ATTEMPTS} 번 모두 어긋나면 {@link IllegalStateException} 이다. 역할 애너테이션이 없다(두
 * 역할 공통).
 */
@Service
class TopicSuggestionService implements TopicSuggestions, TopicInterestNow {

	private final DigestInterestsDirectory digestInterests;

	private final TopicSuggestionDeclines declines;

	private final TagSetDirectory tagSets;

	private final UserTimezones timezones;

	private final CurrentInterests interests;

	private final InstantSource clock;

	TopicSuggestionService(DigestInterestsDirectory digestInterests, TopicSuggestionDeclines declines,
			TagSetDirectory tagSets, UserTimezones timezones, CurrentInterests interests, InstantSource clock) {
		this.digestInterests = Objects.requireNonNull(digestInterests, "digestInterests");
		this.declines = Objects.requireNonNull(declines, "declines");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.timezones = Objects.requireNonNull(timezones, "timezones");
		this.interests = Objects.requireNonNull(interests, "interests");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<TopicSuggestionList> suggestionsOf(UserId user) {
		Objects.requireNonNull(user, "user");
		for (int attempt = 0; attempt < ActiveEvaluations.ATTEMPTS; attempt++) {
			Optional<Attempt> suggested = attempt(user);
			if (suggested.isPresent()) {
				return suggested.get().list();
			}
		}
		throw new IllegalStateException(
				"구독 제안 재료의 태그 체계 활성 버전이 " + ActiveEvaluations.ATTEMPTS + " 번 어긋났다");
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

	/** 한 번의 조회 — 버전이 어긋나면 빈 값 */
	private Optional<Attempt> attempt(UserId user) {
		Optional<DigestInterests> found = this.digestInterests.interestsOf(user);
		if (found.isEmpty()) {
			return Optional.of(new Attempt(Optional.empty()));
		}
		TagSetId version = found.get().tagSet();
		TopicSuggestionDeclineList declined = this.declines.declinesOf(user);
		if (!declined.tagSet().equals(version)) {
			return Optional.empty();
		}
		Instant now = this.clock.instant();
		Optional<InterestLevels> levels = this.interests.levels(user, version, now);
		if (levels.isEmpty()) {
			return Optional.empty();
		}
		List<String> subtopics = TopicSuggestionRule.suggest(levels.get(), subscribed(found.get()),
				declines(declined), this.tagSets.names(version).topicOrder(), now);
		return Optional.of(new Attempt(Optional.of(new TopicSuggestionList(version, subtopics))));
	}

	/** 구독 행이 덮는 중분류 전체 */
	private static Set<String> subscribed(DigestInterests interests) {
		Set<String> subscribed = new HashSet<>();
		for (TopicSubscription subscription : interests.topics()) {
			subscribed.addAll(subscription.subtopics());
		}
		return subscribed;
	}

	private static Map<String, DeclinedTopic> declines(TopicSuggestionDeclineList declined) {
		Map<String, DeclinedTopic> declines = new HashMap<>();
		for (TopicSuggestionDecline decline : declined.declines()) {
			declines.put(decline.subtopic(), new DeclinedTopic(decline.level(), decline.declinedAt()));
		}
		return declines;
	}

	private record Attempt(Optional<TopicSuggestionList> list) {
	}

}
