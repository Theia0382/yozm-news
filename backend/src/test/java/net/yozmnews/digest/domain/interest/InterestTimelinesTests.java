package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.TopicSubscriptionStep;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionCause;
import net.yozmnews.user.TopicSubscriptionChange;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * 기준 시각마다의 관심 · 새로 생긴 관심 · 사라진 관심 · 두 기준 시각 사이의 주제 구독 변경 · 드러난 주제. 기준 시각은 30일 간격이라 앞 기준 시각의
 * 행은 무게 0.5, 두 칸 앞은 0.25 다. 기대값은 손으로 계산한 리터럴이다(K 3 · γ 1 · θ 0.2).
 * <p>
 * 지키는 규칙: digest-R35 · digest-R36
 */
class InterestTimelinesTests {

	private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant T1 = Instant.parse("2026-10-31T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-30T00:00:00Z");

	private static final TagSetId TAG_SET = new TagSetId(7);

	private static final Offset<Double> EXACT = within(1e-12);

	@Test
	void eachReferenceAfterTheFirstHasItsInterestShiftAndSubscriptionSteps() {
		InterestRecord record = new InterestRecord(TAG_SET,
				// c: 사건 1 · 2 를 T1 에 노출, 하루 뒤 좋아요 · d: 사건 3 · 4 를 T1 에 노출, 하루 뒤 싫어요
				List.of(exposure(1, T1), exposure(2, T1), exposure(3, T1), exposure(4, T1)),
				Map.of(event(1), Set.of("c"), event(2), Set.of("c"), event(3), Set.of("d"), event(4),
						Set.of("d")),
				Map.of(),
				List.of(feedback(1, FeedbackValue.LIKE), feedback(2, FeedbackValue.LIKE),
						feedback(3, FeedbackValue.DISLIKE), feedback(4, FeedbackValue.DISLIKE)),
				List.of(
						// a: T0 에 사용자가 두 번 더함 · b: 기본값 · 빈 중분류(계보 없이 지운 키) · e: 사용자가 뺌
						topic(Set.of("a"), TopicSubscriptionAction.ADD, TopicSubscriptionCause.USER, T0),
						topic(Set.of("a"), TopicSubscriptionAction.ADD, TopicSubscriptionCause.USER, T0),
						topic(Set.of("b"), TopicSubscriptionAction.ADD, TopicSubscriptionCause.ONBOARDING,
								T0.plus(Duration.ofDays(1))),
						topic(Set.of(), TopicSubscriptionAction.ADD, TopicSubscriptionCause.USER,
								T1.plus(Duration.ofDays(2))),
						topic(Set.of("e"), TopicSubscriptionAction.REMOVE, TopicSubscriptionCause.USER,
								T1.plus(Duration.ofDays(5)))),
				List.of(), Map.of(), StorylineFlows.NONE);

		List<InterestPoint> points = InterestTimelines.points(record, List.of(T0, T1, T2));

		assertThat(points).extracting(InterestPoint::reference).containsExactly(T1, T2);
		InterestPoint first = points.get(0);
		// T1: a 는 더함 둘(무게 0.5) → S = 1 → x = 1/3. c · d 의 T1 노출은 창 끝(T1) 밖
		assertThat(first.levels()).containsOnlyKeys("a");
		assertThat(first.levels().get("a")).isCloseTo(0.3333333333333333, EXACT);
		assertThat(first.entered()).containsExactly("a");
		assertThat(first.left()).isEmpty();
		assertThat(first.subscriptionChanges()).as("T0 이상 T1 미만, 사용자가 바꾸고 중분류가 있는 행").containsExactly(
				new TopicSubscriptionStep(T0, TopicSubscriptionAction.ADD, Set.of("a")),
				new TopicSubscriptionStep(T0, TopicSubscriptionAction.ADD, Set.of("a")));

		InterestPoint second = points.get(1);
		// T2: a 는 무게 0.25 둘 → x = 0.5/3 = 1/6(관심 있음 아님 → 사라짐), c 는 노출 둘(0.5) · 좋아요 둘 → x = 1/4,
		// d 는 싫어요 둘 → x = −1/4
		assertThat(second.levels().get("a")).isCloseTo(0.16666666666666666, EXACT);
		assertThat(second.levels().get("c")).isCloseTo(0.25, EXACT);
		assertThat(second.levels().get("d")).isCloseTo(-0.25, EXACT);
		assertThat(second.entered()).containsExactly("c");
		assertThat(second.left()).containsExactly("a");
		assertThat(second.subscriptionChanges()).containsExactly(
				new TopicSubscriptionStep(T1.plus(Duration.ofDays(5)), TopicSubscriptionAction.REMOVE, Set.of("e")));

		// 드러난 주제 — |x| ≥ 0.2: a(1/3) · c(1/4) · d(−1/4). e 는 뺌 하나(25일 전) → |x| 는 0.2 미만
		assertThat(InterestTimelines.prominent(points)).containsExactlyInAnyOrder("a", "c", "d");
	}

	@Test
	void theReferencesAreAtLeastTwoAndNotGoingBack() {
		InterestRecord empty = new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(),
				Map.of(), StorylineFlows.NONE);

		assertThatIllegalArgumentException().isThrownBy(() -> InterestTimelines.points(empty, List.of(T0)));
		assertThatIllegalArgumentException().isThrownBy(() -> InterestTimelines.points(empty, List.of(T1, T0)));
		assertThat(InterestTimelines.points(empty, List.of(T0, T1))).singleElement()
			.satisfies(point -> assertThat(point.levels()).isEmpty());
	}

	@Test
	void aRepeatedReferenceHasTheSameInterestAndNoShiftOrStep() {
		// 기간이 시작되는 정각에 부르면 앞 기간 끝과 진행 중 기간의 기준 시각이 같다 — a: T0 에 사용자가 두 번 더함
		InterestRecord record = new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(),
				List.of(topic(Set.of("a"), TopicSubscriptionAction.ADD, TopicSubscriptionCause.USER, T0),
						topic(Set.of("a"), TopicSubscriptionAction.ADD, TopicSubscriptionCause.USER, T0)),
				List.of(), Map.of(), StorylineFlows.NONE);

		List<InterestPoint> points = InterestTimelines.points(record, List.of(T0, T1, T1));

		assertThat(points).extracting(InterestPoint::reference).containsExactly(T1, T1);
		assertThat(points.get(0).entered()).containsExactly("a");
		InterestPoint repeated = points.get(1);
		assertThat(repeated.levels()).as("같은 창 — 같은 관심").isEqualTo(points.get(0).levels());
		assertThat(repeated.levels().get("a")).isCloseTo(0.3333333333333333, EXACT);
		assertThat(repeated.entered()).isEmpty();
		assertThat(repeated.left()).isEmpty();
		assertThat(repeated.subscriptionChanges()).as("[T1, T1) 은 비었다").isEmpty();
	}

	/**
	 * 꼭대기 9 의 사건 넷을 T1 에 노출하고 하루 뒤 모두 좋아요 — 스토리라인 관심 x = 4w ÷ (4w + 3), w 는 노출 뒤 30일마다 반이다. T2(30일
	 * 뒤)에 2 ÷ 5 = 0.4 로 만들 기준 0.35 를 넘어 보이게 되고, T3(60일 뒤)에 1 ÷ 4 = 0.25 로 남길 기준 0.2 위라 남고, T4(90일 뒤)에
	 * 0.5 ÷ 3.5 = 1/7 로 사라진다
	 */
	@Test
	void aFlowAppearsAtTheFollowThresholdStaysAboveTheKeepThresholdAndLeavesBelowIt() {
		StorylineId top = new StorylineId(9);
		Instant t3 = T2.plus(Duration.ofDays(30));
		Instant t4 = t3.plus(Duration.ofDays(30));
		InterestRecord record = new InterestRecord(TAG_SET,
				List.of(exposure(1, T1), exposure(2, T1), exposure(3, T1), exposure(4, T1)), Map.of(),
				Map.of(event(1), List.of(top), event(2), List.of(top), event(3), List.of(top), event(4), List.of(top)),
				List.of(feedback(1, FeedbackValue.LIKE), feedback(2, FeedbackValue.LIKE), feedback(3, FeedbackValue.LIKE),
						feedback(4, FeedbackValue.LIKE)),
				List.of(), List.of(), Map.of(), new StorylineFlows(Map.of(top, 0), Set.of()));

		List<InterestPoint> points = InterestTimelines.points(record, List.of(T0, T1, T2, t3, t4));

		assertThat(points).extracting(InterestPoint::reference).containsExactly(T1, T2, t3, t4);
		assertThat(points.get(0).storylineEntered()).isEmpty();
		assertThat(points.get(0).storylineLeft()).isEmpty();
		assertThat(points.get(1).storylineLevels().get(top)).isCloseTo(0.4, EXACT);
		assertThat(points.get(1).storylineEntered()).containsExactly(top);
		assertThat(points.get(1).storylineLeft()).isEmpty();
		assertThat(points.get(2).storylineLevels().get(top)).isCloseTo(0.25, EXACT);
		assertThat(points.get(2).storylineEntered()).isEmpty();
		assertThat(points.get(2).storylineLeft()).isEmpty();
		assertThat(points.get(3).storylineLevels().get(top)).isCloseTo(1.0 / 7.0, EXACT);
		assertThat(points.get(3).storylineEntered()).isEmpty();
		assertThat(points.get(3).storylineLeft()).containsExactly(top);
	}

	@Test
	void pointsWithoutFlowsCarryNoStorylineValues() {
		InterestRecord empty = new InterestRecord(TAG_SET, List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(),
				Map.of(), StorylineFlows.NONE);

		assertThat(InterestTimelines.points(empty, List.of(T0, T1))).singleElement().satisfies(point -> {
			assertThat(point.storylineLevels()).isEmpty();
			assertThat(point.storylineEntered()).isEmpty();
			assertThat(point.storylineLeft()).isEmpty();
		});
	}

	private static EventId event(long id) {
		return new EventId(id);
	}

	/** 사건 하나인 항목의 노출 행(몫 1) — 다이제스트는 사건마다 다르다 */
	private static ExposureRow exposure(long event, Instant at) {
		return new ExposureRow(event, LocalDate.ofInstant(at, ZoneOffset.UTC), event(event), at, 1.0);
	}

	private static FeedbackChange feedback(long event, FeedbackValue value) {
		return new FeedbackChange(event(event), event(event), value, 1, T1.plus(Duration.ofDays(1)));
	}

	private static TopicSubscriptionChange topic(Set<String> subtopics, TopicSubscriptionAction action,
			TopicSubscriptionCause cause, Instant at) {
		return new TopicSubscriptionChange("key", TAG_SET, subtopics, action, cause, at);
	}

}
