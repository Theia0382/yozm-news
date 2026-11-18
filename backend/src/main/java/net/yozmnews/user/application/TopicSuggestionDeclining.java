package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.TopicInterestNow;
import net.yozmnews.user.domain.suggestion.DeclinableTopics;
import net.yozmnews.user.domain.suggestion.RecordedDecline;
import net.yozmnews.user.domain.suggestion.TopicSuggestionDeclineRepository;
import org.springframework.stereotype.Service;

/**
 * 구독 제안 거절 — 화면이 본 체계 버전의 주제 중분류 하나를 거절 때의 관심과 함께 적는다. docs/specs/user.md §4 · user-R64
 * <p>
 * 트랜잭션이 없다 — 거절 때의 관심({@link TopicInterestNow} — {@code digest} 가 구현)은 여러 모듈을 읽으므로 잠금 · 연결을 쥐지 않고 부르고,
 * 쓰기는 한 문장의 upsert 다. 설정 행을 잠그거나 따라가지 않는다 — 태그 선호가 아니고 거절 행은 자기 버전을 갖는다. 관심을 읽은 뒤 쓰기 전에
 * 활성 전환이 일어나도 행은 화면이 본 버전으로 적히고 읽을 때 계보로 풀린다. 이 처리는 관심 신호를 남기지 않는다.
 */
@Service
public class TopicSuggestionDeclining {

	private final TagSetDirectory tagSets;

	private final TopicInterestNow interests;

	private final TopicSuggestionDeclineRepository declines;

	TopicSuggestionDeclining(TagSetDirectory tagSets, TopicInterestNow interests,
			TopicSuggestionDeclineRepository declines) {
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
		this.interests = Objects.requireNonNull(interests, "interests");
		this.declines = Objects.requireNonNull(declines, "declines");
	}

	/**
	 * @param seen 화면이 본 체계 버전
	 * @param subtopic 그 버전의 주제 중분류 전체 키
	 * @throws UnknownTopicException 지금 활성 버전과 같은 {@code seen} 의 중분류 키가 아닐 때 — 아무것도 바꾸지 않는다
	 */
	public DeclineOutcome decline(UserId user, TagSetId seen, String subtopic, Instant now) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(seen, "seen");
		Objects.requireNonNull(subtopic, "subtopic");
		Objects.requireNonNull(now, "now");
		TagSetId active = this.tagSets.activeTagSet().id();
		if (!active.equals(seen)) {
			return new DeclineOutcome.TagSetChanged(active);
		}
		if (!DeclinableTopics.isSubtopic(this.tagSets.tagSet(seen), subtopic)) {
			throw new UnknownTopicException(subtopic);
		}
		OptionalDouble level = this.interests.levelOf(user, seen, subtopic);
		if (level.isEmpty()) {
			return new DeclineOutcome.TagSetChanged(this.tagSets.activeTagSet().id());
		}
		this.declines.upsert(user, new RecordedDecline(seen, subtopic, level.getAsDouble(), now));
		return new DeclineOutcome.Declined(subtopic);
	}

	/** 구독 제안 거절의 결과 */
	public sealed interface DeclineOutcome {

		/** 적었다 */
		record Declined(String subtopic) implements DeclineOutcome {
		}

		/** 화면이 본 체계 버전이 지금 활성 버전이 아니다 — 적지 않았다 */
		record TagSetChanged(TagSetId active) implements DeclineOutcome {
		}

	}

	/** 그 버전의 주제 중분류 키가 아니다 — 요청 형식의 문제다 */
	public static final class UnknownTopicException extends RuntimeException {

		UnknownTopicException(String key) {
			super("체계의 주제 중분류 키가 아니다: " + key);
		}

	}

}
