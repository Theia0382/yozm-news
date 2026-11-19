package net.yozmnews.digest.domain.selection;

import static net.yozmnews.digest.domain.selection.Facts.article;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 사건의 공유 값 — 주제 · 지역은 평가 있는 소속 기사 절반 이상에 붙은 키, 중요도는 최대, {@code low_confidence} 는 모두일 때.
 * <p>
 * 지키는 규칙: 사건의 값(docs/specs/digest.md §5 후보 선별)
 */
class EventFactsTests {

	private static final EventId EVENT = new EventId(7);

	@Test
	void twoArticlesWithDifferentTopicsGiveTheEventBothTopics() {
		EventFacts facts = EventFacts.of(EVENT, List.of(article(1).subtopics("a").evaluation(),
				article(2).subtopics("b").evaluation()));

		assertThat(facts.subtopics()).containsExactlyInAnyOrder("a", "b");
	}

	@Test
	void aMinorityTopicIsNotTheEventsTopicAndUnclassifiedArticlesCountInTheDenominator() {
		EventFacts majority = EventFacts.of(EVENT, List.of(article(1).subtopics("a").evaluation(),
				article(2).subtopics("a").evaluation(), article(3).subtopics("b").evaluation()));
		assertThat(majority.subtopics()).containsExactly("a");

		// 대조 — 미분류 둘이 분모에 들면 a 는 1/3 이라 주제가 아니다
		EventFacts diluted = EventFacts.of(EVENT, List.of(article(1).subtopics("a").evaluation(),
				article(2).unclassified().evaluation(), article(3).unclassified().evaluation()));
		assertThat(diluted.subtopics()).isEmpty();

		// 경계 — 정확히 절반이면 든다
		EventFacts half = EventFacts.of(EVENT, List.of(article(1).subtopics("a", "b").evaluation(),
				article(2).subtopics("a").evaluation(), article(3).subtopics("c").evaluation(),
				article(4).subtopics("c").evaluation()));
		assertThat(half.subtopics()).containsExactlyInAnyOrder("a", "c");
	}

	@Test
	void regionsFollowTheSameShareRule() {
		EventFacts facts = EventFacts.of(EVENT, List.of(article(1).regions("domestic").evaluation(),
				article(2).regions("domestic", "world.asia").evaluation(), article(3).regions("world.asia").evaluation(),
				article(4).regions("world.europe").evaluation()));

		assertThat(facts.regions()).containsExactlyInAnyOrder("domestic", "world.asia");
	}

	@Test
	void theImportanceIsTheHighestOfTheMembers() {
		EventFacts facts = EventFacts.of(EVENT, List.of(article(1).importance(2.0).evaluation(),
				article(2).importance(4.0).evaluation(), article(3).importance(null).evaluation()));
		assertThat(facts.importance()).isEqualTo(4.0);

		EventFacts none = EventFacts.of(EVENT, List.of(article(1).importance(null).evaluation()));
		assertThat(none.importance()).isNull();
	}

	@Test
	void lowConfidenceOnlyWhenEveryMemberIsLowConfidence() {
		assertThat(EventFacts.of(EVENT, List.of(article(1).lowConfidence().evaluation(),
				article(2).lowConfidence().evaluation())).lowConfidence()).isTrue();
		assertThat(EventFacts.of(EVENT, List.of(article(1).lowConfidence().evaluation(),
				article(2).evaluation())).lowConfidence()).isFalse();
	}

	@Test
	void anEventWithoutEvaluatedMembersHasNoValue() {
		assertThatIllegalArgumentException().isThrownBy(() -> EventFacts.of(EVENT, List.of()));
	}

	@Test
	void theShareRuleKeepsKeysAtHalfOrMore() {
		assertThat(EventShares.keys(List.of())).isEmpty();
		assertThat(EventShares.keys(List.of(Set.of("a"), Set.of("b"), Set.of("b")))).containsExactly("b");
		assertThat(EventShares.SHARE_THRESHOLD).isEqualTo(0.5);
	}

}
