package net.yozmnews.user.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.user.domain.summary.SummaryPlan.Action;
import net.yozmnews.user.domain.summary.SummaryPlan.Flow;
import org.junit.jupiter.api.Test;

/**
 * 정리의 처분 — 새로 만드는 것은 관심 흐름뿐이고, 정리한 흐름은 관심 흐름이거나 남길 흐름인 동안 남으며, 재료가 그대로면 부르지 않는다. 추적 대상이
 * 달라지면 처음부터, 새 사건이 있으면 그 사건만 넣는다. 연속 실패로 쉬는 흐름과 호출 상한 밖의 흐름은 부르지 않는다.
 * <p>
 * 지키는 규칙: docs/specs/user.md §5 의 정리 처분. 기대값은 손으로 쓴 처분이다.
 */
class SummaryPlanTests {

	private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

	private static final Instant DAY1 = Instant.parse("2026-11-20T00:00:00Z");

	private static final Instant DAY2 = Instant.parse("2026-11-21T00:00:00Z");

	private static final Instant DAY3 = Instant.parse("2026-11-22T00:00:00Z");

	@Test
	void aFollowedFlowWithoutASummaryIsCreatedWithItsEventsInTimeOrder() {
		Flow flow = new Flow(1, 10, Set.of(10L), false, null, null);

		assertThat(decide(List.of(flow), Set.of(10L), Set.of(), Map.of(1L, Map.of(103L, DAY3, 101L, DAY1, 102L, DAY2))))
			.containsExactly(new Action.Create(1, Set.of(10L), List.of(101L, 102L, 103L), Set.of(101L, 102L, 103L)));
	}

	@Test
	void aFollowedHeadIsEnoughAmongTheTrackedTargets() {
		Flow flow = new Flow(1, 11, Set.of(10L, 11L), false, null, null);

		assertThat(decide(List.of(flow), Set.of(11L), Set.of(), Map.of(1L, Map.of(101L, DAY1))))
			.containsExactly(new Action.Create(1, Set.of(10L, 11L), List.of(101L), Set.of(101L)));
	}

	@Test
	void aFlowThatIsOnlyKeptOrNotJudgedIsNotCreated() {
		Flow keptOnly = new Flow(1, 10, Set.of(10L), false, null, null);
		Flow unjudged = new Flow(2, 20, Set.of(20L), true, null, null);

		assertThat(decide(List.of(keptOnly, unjudged), Set.of(), Set.of(10L),
				Map.of(1L, Map.of(101L, DAY1), 2L, Map.of(201L, DAY1))))
			.isEmpty();
	}

	@Test
	void aFollowedFlowWithoutEventsIsNotCreated() {
		Flow flow = new Flow(1, 10, Set.of(10L), false, null, null);

		assertThat(decide(List.of(flow), Set.of(10L), Set.of(), Map.of(1L, Map.of()))).isEmpty();
	}

	@Test
	void aSummarizedFlowStaysWhileFollowedOrKeptAndIsRemovedOtherwise() {
		Flow followedOnly = new Flow(1, 10, Set.of(10L), true, summary(1, Set.of(10L), Set.of(101L), 0), null);
		Flow keptOnly = new Flow(2, 20, Set.of(20L), false, summary(2, Set.of(20L), Set.of(201L), 0), null);
		Flow neither = new Flow(3, 30, Set.of(30L), false, summary(3, Set.of(30L), Set.of(301L), 0), null);

		assertThat(decide(List.of(followedOnly, keptOnly, neither), Set.of(10L), Set.of(20L),
				Map.of(1L, Map.of(101L, DAY1), 2L, Map.of(201L, DAY1), 3L, Map.of(301L, DAY1))))
			.containsExactly(new Action.Keep(1), new Action.Keep(2), new Action.Remove(3));
	}

	@Test
	void newEventsExtendTheSummaryWithOnlyThoseEvents() {
		Flow flow = new Flow(1, 10, Set.of(10L), false, summary(1, Set.of(10L), Set.of(101L, 102L), 4), null);

		assertThat(decide(List.of(flow), Set.of(), Set.of(10L),
				Map.of(1L, Map.of(101L, DAY1, 102L, DAY2, 104L, DAY3, 103L, DAY3))))
			.containsExactly(new Action.Extend(1, 4, Set.of(10L), List.of(103L, 104L), Set.of(101L, 102L, 103L, 104L)));
	}

	@Test
	void eventsThatLeftTheFlowDoNotCallAgain() {
		Flow flow = new Flow(1, 10, Set.of(10L), false, summary(1, Set.of(10L), Set.of(101L, 102L), 0), null);

		assertThat(decide(List.of(flow), Set.of(10L), Set.of(), Map.of(1L, Map.of(101L, DAY1))))
			.containsExactly(new Action.Keep(1));
	}

	@Test
	void changedTrackedTargetsRebuildFromAllEvents() {
		Flow flow = new Flow(1, 10, Set.of(10L, 11L), false, summary(1, Set.of(10L), Set.of(101L), 2), null);

		assertThat(decide(List.of(flow), Set.of(10L), Set.of(), Map.of(1L, Map.of(101L, DAY1, 111L, DAY2))))
			.containsExactly(new Action.Rebuild(1, 2, Set.of(10L, 11L), List.of(101L, 111L), Set.of(101L, 111L)));
	}

	@Test
	void aRebuildWithoutEventsKeepsTheSummary() {
		Flow flow = new Flow(1, 11, Set.of(11L), false, summary(1, Set.of(10L), Set.of(101L), 2), null);

		assertThat(decide(List.of(flow), Set.of(11L), Set.of(), Map.of(1L, Map.of()))).containsExactly(new Action.Keep(1));
	}

	@Test
	void onlyTheLatestSixtyEventsAreSentAndTheRestAreStillCovered() {
		Map<Long, Instant> events = new HashMap<>();
		List<Long> latestSixty = new ArrayList<>();
		Set<Long> all = new HashSet<>();
		for (long i = 1; i <= 61; i++) {
			events.put(i, DAY1.plusSeconds(i));
			all.add(i);
			if (i >= 2) {
				latestSixty.add(i);
			}
		}
		Flow created = new Flow(1, 10, Set.of(10L), false, null, null);
		Flow extended = new Flow(2, 20, Set.of(20L), false, summary(2, Set.of(20L), Set.of(1_000L), 0), null);

		assertThat(decide(List.of(created, extended), Set.of(10L, 20L), Set.of(), Map.of(1L, events, 2L, events)))
			.containsExactly(new Action.Create(1, Set.of(10L), latestSixty, all),
					new Action.Extend(2, 0, Set.of(20L), latestSixty, all));
	}

	@Test
	void everyTopWithEventsGetsAShareBeforeTheLatestEventsFillTheRest() {
		Map<Long, Instant> crowded = new HashMap<>();
		Set<Long> all = new HashSet<>();
		for (long i = 1; i <= 70; i++) {
			crowded.put(i, DAY2.plusSeconds(i));
			all.add(i);
		}
		Map<Long, Instant> old = Map.of(1_001L, DAY1.plusSeconds(1), 1_002L, DAY1);
		all.addAll(old.keySet());
		List<Long> expected = new ArrayList<>(List.of(1_002L, 1_001L));
		for (long i = 13; i <= 70; i++) {
			expected.add(i);
		}
		Flow flow = new Flow(1, 10, Set.of(10L, 20L, 30L), false, null, null);

		// 사건이 있는 꼭대기 둘의 몫은 30 — 붐비는 꼭대기의 늦은 30(41 ~ 70)과 오래된 꼭대기의 둘, 남는 28 자리는 늦은 순(13 ~ 40)
		assertThat(SummaryPlan.decide(List.of(flow), Set.of(10L), Set.of(),
				Map.of(1L, Map.of(10L, crowded, 20L, old, 30L, Map.of())), NOW))
			.containsExactly(new Action.Create(1, Set.of(10L, 20L, 30L), expected, all));
	}

	@Test
	void newEventsOfEveryTopGetAShareToo() {
		Map<Long, Instant> crowded = new HashMap<>();
		Set<Long> all = new HashSet<>(Set.of(500L, 2_001L));
		for (long i = 1; i <= 70; i++) {
			crowded.put(i, DAY2.plusSeconds(i));
			all.add(i);
		}
		crowded.put(500L, DAY1);
		List<Long> expected = new ArrayList<>(List.of(2_001L));
		for (long i = 12; i <= 70; i++) {
			expected.add(i);
		}
		Flow flow = new Flow(1, 10, Set.of(10L, 20L), false, summary(1, Set.of(10L, 20L), Set.of(500L), 3), null);

		// 새 사건이 있는 꼭대기 둘의 몫은 30 — 붐비는 꼭대기의 늦은 30 과 다른 꼭대기의 하나, 남는 29 자리는 늦은 순(12 ~ 40)
		assertThat(SummaryPlan.decide(List.of(flow), Set.of(10L), Set.of(),
				Map.of(1L, Map.of(10L, crowded, 20L, Map.of(2_001L, DAY1.plusSeconds(5)))), NOW))
			.containsExactly(new Action.Extend(1, 3, Set.of(10L, 20L), expected, all));
	}

	@Test
	void aFlowRestsForAsManyDaysAsItsConsecutiveFailures() {
		Instant failedAt = Instant.parse("2026-11-21T00:00:00Z");
		SummaryAttempt twice = new SummaryAttempt(1, 2, 0, 0, 2, "UNREADABLE_OUTPUT", failedAt);
		Flow unsummarized = new Flow(1, 10, Set.of(10L), false, null, twice);
		Flow summarized = new Flow(2, 20, Set.of(20L), false, summary(2, Set.of(20L), Set.of(), 0),
				new SummaryAttempt(2, 2, 0, 0, 2, "UNREADABLE_OUTPUT", failedAt));
		Map<Long, Map<Long, Instant>> events = Map.of(1L, Map.of(101L, DAY1), 2L, Map.of(201L, DAY1));

		assertThat(SummaryPlan.decide(List.of(unsummarized, summarized), Set.of(10L, 20L), Set.of(),
				underFirstTop(List.of(unsummarized, summarized), events), failedAt.plus(Duration.ofDays(2)).minusSeconds(1)))
			.containsExactly(new Action.Keep(2));
		assertThat(SummaryPlan.decide(List.of(unsummarized, summarized), Set.of(10L, 20L), Set.of(),
				underFirstTop(List.of(unsummarized, summarized), events), failedAt.plus(Duration.ofDays(2))))
			.containsExactly(new Action.Create(1, Set.of(10L), List.of(101L), Set.of(101L)),
					new Action.Extend(2, 0, Set.of(20L), List.of(201L), Set.of(201L)));
	}

	@Test
	void aRestingFlowIsStillRemoved() {
		Flow flow = new Flow(1, 10, Set.of(10L), false, summary(1, Set.of(10L), Set.of(101L), 0),
				new SummaryAttempt(1, 1, 0, 0, 1, "OUTPUT_TRUNCATED", NOW.minusSeconds(60)));

		assertThat(decide(List.of(flow), Set.of(), Set.of(), Map.of(1L, Map.of(101L, DAY1))))
			.containsExactly(new Action.Remove(1));
	}

	@Test
	void anAttemptWithoutFailuresDoesNotRest() {
		Flow flow = new Flow(1, 10, Set.of(10L), false, null, new SummaryAttempt(1, 3, 100, 20, 0, null, null));

		assertThat(decide(List.of(flow), Set.of(10L), Set.of(), Map.of(1L, Map.of(101L, DAY1))))
			.containsExactly(new Action.Create(1, Set.of(10L), List.of(101L), Set.of(101L)));
	}

	@Test
	void callsComeFirstCreatesThenRebuildsThenExtendsTrackedFirstThenById() {
		Flow extendTracked = new Flow(1, 10, Set.of(10L), true, summary(1, Set.of(10L), Set.of(), 0), null);
		Flow rebuild = new Flow(2, 20, Set.of(20L), false, summary(2, Set.of(29L), Set.of(), 0), null);
		Flow create = new Flow(3, 30, Set.of(30L), false, null, null);
		Flow createTracked = new Flow(4, 40, Set.of(40L), true, null, null);
		Flow kept = new Flow(5, 50, Set.of(50L), false, summary(5, Set.of(50L), Set.of(501L), 0), null);
		Map<Long, Map<Long, Instant>> events = Map.of(1L, Map.of(101L, DAY1), 2L, Map.of(201L, DAY1), 3L,
				Map.of(301L, DAY1), 4L, Map.of(401L, DAY1), 5L, Map.of(501L, DAY1));

		assertThat(decide(List.of(kept, create, rebuild, extendTracked, createTracked), Set.of(10L, 20L, 30L, 40L, 50L),
				Set.of(), events))
			.extracting(Action::id, action -> action.getClass().getSimpleName())
			.containsExactly(org.assertj.core.groups.Tuple.tuple(4L, "Create"),
					org.assertj.core.groups.Tuple.tuple(3L, "Create"), org.assertj.core.groups.Tuple.tuple(2L, "Rebuild"),
					org.assertj.core.groups.Tuple.tuple(1L, "Extend"), org.assertj.core.groups.Tuple.tuple(5L, "Keep"));
	}

	@Test
	void callsBeyondTwentyAreDeferredAndADeferredSummaryIsKept() {
		List<Flow> flows = new ArrayList<>();
		Map<Long, Map<Long, Instant>> events = new HashMap<>();
		Set<Long> followed = new HashSet<>();
		for (long id = 1; id <= 21; id++) {
			flows.add(new Flow(id, id * 10, Set.of(id * 10), false, null, null));
			events.put(id, Map.of(id * 100, DAY1));
			followed.add(id * 10);
		}
		flows.add(new Flow(22, 220, Set.of(220L), false, summary(22, Set.of(220L), Set.of(), 0), null));
		events.put(22L, Map.of(2_200L, DAY1));
		followed.add(220L);

		List<Action> decided = decide(flows, followed, Set.of(), events);

		assertThat(decided).hasSize(21);
		assertThat(decided.subList(0, 20)).allMatch(Action.Create.class::isInstance)
			.extracting(Action::id)
			.containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
		assertThat(decided.get(20)).isEqualTo(new Action.Keep(22));
	}

	@Test
	void materialsAreNeededOnlyForSummarizedOrFollowedFlows() {
		Flow summarized = new Flow(1, 10, Set.of(10L), false, summary(1, Set.of(10L), Set.of(), 0), null);
		Flow followed = new Flow(2, 21, Set.of(20L, 21L), false, null, null);
		Flow other = new Flow(3, 30, Set.of(30L), true, null, null);

		assertThat(SummaryPlan.needingMaterials(List.of(summarized, followed, other), Set.of(21L)))
			.containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void aBranchIsSummarizedOnlyWhenItsOwnHeadIsFollowed() {
		// 갈라지기 전 꼭대기 5 가 두 갈래(헤드 82 · 헤드 423)에 함께 든다
		Flow tracked = new Flow(1, 82, Set.of(5L, 82L), true, null, null);
		Flow sibling = new Flow(2, 423, Set.of(5L, 423L), false, null, null);
		Map<Long, Map<Long, Instant>> events = Map.of(1L, Map.of(101L, DAY1), 2L, Map.of(201L, DAY1));

		// 관심 흐름이 5 · 82 면 형제 갈래는 5 를 함께 가져도 만들지 않는다
		assertThat(decide(List.of(tracked, sibling), Set.of(5L, 82L), Set.of(), events))
			.containsExactly(new Action.Create(1, Set.of(5L, 82L), List.of(101L), Set.of(101L)));
		assertThat(SummaryPlan.needingMaterials(List.of(tracked, sibling), Set.of(5L, 82L))).containsExactly(1L);
		// 대조 — 형제의 헤드 423 도 관심 흐름이면 둘 다
		assertThat(decide(List.of(tracked, sibling), Set.of(5L, 82L, 423L), Set.of(), events)).containsExactly(
				new Action.Create(1, Set.of(5L, 82L), List.of(101L), Set.of(101L)),
				new Action.Create(2, Set.of(5L, 423L), List.of(201L), Set.of(201L)));
	}

	@Test
	void aSummaryOfABranchIsRemovedWhenOnlyTheSharedTopIsKept() {
		Flow branch = new Flow(1, 423, Set.of(5L, 423L), false, summary(1, Set.of(5L, 423L), Set.of(201L), 0), null);

		assertThat(decide(List.of(branch), Set.of(), Set.of(5L), Map.of(1L, Map.of(201L, DAY1))))
			.containsExactly(new Action.Remove(1));
		assertThat(decide(List.of(branch), Set.of(), Set.of(423L), Map.of(1L, Map.of(201L, DAY1))))
			.containsExactly(new Action.Keep(1));
	}

	/** 사건을 그 흐름의 가장 작은 꼭대기에 둔 처분 — 꼭대기마다의 사건을 따로 주는 시험은 {@link SummaryPlan#decide} 를 직접 부른다 */
	private static List<Action> decide(List<Flow> flows, Set<Long> followed, Set<Long> kept,
			Map<Long, Map<Long, Instant>> events) {
		return SummaryPlan.decide(flows, followed, kept, underFirstTop(flows, events), NOW);
	}

	private static Map<Long, Map<Long, Map<Long, Instant>>> underFirstTop(List<Flow> flows,
			Map<Long, Map<Long, Instant>> events) {
		Map<Long, Map<Long, Map<Long, Instant>>> nested = new HashMap<>();
		for (Flow flow : flows) {
			if (events.containsKey(flow.id())) {
				Map<Long, Map<Long, Instant>> byTop = new HashMap<>();
				flow.tops().forEach(top -> byTop.put(top, Map.of()));
				byTop.put(java.util.Collections.min(flow.tops()), events.get(flow.id()));
				nested.put(flow.id(), byTop);
			}
		}
		return nested;
	}

	private static StorylineSummary summary(long id, Set<Long> tops, Set<Long> events, int version) {
		SummaryText text = new SummaryText("제목", "요약", "최근");
		Map<Long, SummaryText> texts = new HashMap<>();
		tops.forEach(top -> texts.put(top, text));
		Map<Long, Long> covered = new HashMap<>();
		events.forEach(event -> covered.put(event, event + 10_000));
		return new StorylineSummary(id, text, texts, covered, "ANTHROPIC", "model", 1, 1, DAY1, DAY1, version);
	}

}
