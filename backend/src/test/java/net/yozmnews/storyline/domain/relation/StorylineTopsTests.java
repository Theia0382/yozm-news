package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.storyline.domain.storyline.DirectMembership;
import org.junit.jupiter.api.Test;

/**
 * 사건마다 포함 나무의 꼭대기와 그 순서.
 * <p>
 * 지키는 규칙: storyline-R21(소속 점수는 싣지 않는다) · storyline-R25(포함은 상위 하나 — 꼭대기가 하나로 정해진다)
 */
class StorylineTopsTests {

	/** 1 ⊃ 2 ⊃ 3, 10 ⊃ 11, 7 → 1(이어짐 — 꼭대기가 아니다) */
	private static final RelationGraph GRAPH = new RelationGraph(List.of(new Relation(1, 2, RelationKind.CONTAINS),
			new Relation(2, 3, RelationKind.CONTAINS), new Relation(10, 11, RelationKind.CONTAINS),
			new Relation(7, 1, RelationKind.FOLLOWS)));

	@Test
	void theTopIsTheRootOfContainmentAndAStorylineWithoutParentIsItsOwnTop() {
		Map<Long, List<Long>> tops = StorylineTops.of(List.of(new DirectMembership(100, 3, 0.9),
				new DirectMembership(101, 7, 0.8), new DirectMembership(102, 20, 0.7)), GRAPH);

		// 3 의 뿌리는 1, 이어짐의 앞(7)은 자기 자신이 꼭대기, 관계 없는 20 도 자기
		assertThat(tops).containsExactlyInAnyOrderEntriesOf(Map.of(100L, List.of(1L), 101L, List.of(7L), 102L, List.of(20L)));
	}

	@Test
	void membershipsUnderOneRootAreOneTopWithTheirHighestScore() {
		// 사건 100 은 같은 나무의 2 와 3 에 직접 들었고(1 의 나무), 나무 10 에도 들었다
		Map<Long, List<Long>> tops = StorylineTops.of(List.of(new DirectMembership(100, 2, 0.60),
				new DirectMembership(100, 3, 0.95), new DirectMembership(100, 11, 0.90)), GRAPH);

		// 나무 1 의 가장 높은 점수 0.95 > 나무 10 의 0.90
		assertThat(tops).containsExactlyEntriesOf(Map.of(100L, List.of(1L, 10L)));
	}

	@Test
	void topsAreOrderedByScoreWithScorelessTopsLastAndTiesByTopId() {
		Map<Long, List<Long>> byScore = StorylineTops.of(
				List.of(new DirectMembership(100, 3, 0.70), new DirectMembership(100, 11, 0.85)), GRAPH);
		assertThat(byScore.get(100L)).containsExactly(10L, 1L);

		// 점수 없는 소속(합치기)만 있는 꼭대기는 점수가 낮아도 점수 있는 꼭대기 뒤
		Map<Long, List<Long>> scoreless = StorylineTops.of(
				List.of(new DirectMembership(100, 11, null), new DirectMembership(100, 3, 0.10)), GRAPH);
		assertThat(scoreless.get(100L)).containsExactly(1L, 10L);
		// 점수가 0 이어도 점수 없는 꼭대기보다 앞 — 점수 없음은 0 점이 아니다(꼭대기 ID 는 점수 없는 쪽이 작다)
		Map<Long, List<Long>> zero = StorylineTops.of(
				List.of(new DirectMembership(100, 3, null), new DirectMembership(100, 11, 0.0)), GRAPH);
		assertThat(zero.get(100L)).containsExactly(10L, 1L);

		// 대조 — 같은 나무에 점수 있는 소속이 하나라도 있으면 그 점수로 선다
		Map<Long, List<Long>> mixed = StorylineTops.of(List.of(new DirectMembership(100, 11, null),
				new DirectMembership(100, 10, 0.50), new DirectMembership(100, 3, 0.40)), GRAPH);
		assertThat(mixed.get(100L)).containsExactly(10L, 1L);

		// 같은 점수면 꼭대기 ID 오름차순 — 둘 다 점수 없음도 같다
		Map<Long, List<Long>> tie = StorylineTops.of(
				List.of(new DirectMembership(100, 11, 0.5), new DirectMembership(100, 3, 0.5)), GRAPH);
		assertThat(tie.get(100L)).containsExactly(1L, 10L);
		Map<Long, List<Long>> bothScoreless = StorylineTops.of(
				List.of(new DirectMembership(100, 20, null), new DirectMembership(100, 11, null)), GRAPH);
		assertThat(bothScoreless.get(100L)).containsExactly(10L, 20L);
	}

	@Test
	void eventsWithoutMembershipAreAbsent() {
		assertThat(StorylineTops.of(List.of(), GRAPH)).isEmpty();
	}

}
