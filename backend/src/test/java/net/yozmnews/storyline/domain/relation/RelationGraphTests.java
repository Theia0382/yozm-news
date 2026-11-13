package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import net.yozmnews.storyline.domain.queue.LinkingLease;
import org.junit.jupiter.api.Test;

/**
 * 관계의 구조 — 상위 · 하위 · 조상 · 순환, 그리고 관계 · 기준 · 대기 행 값의 제약.
 * <p>
 * 지키는 규칙: storyline-R25 · storyline-R27 · storyline-R31
 */
class RelationGraphTests {

	/** 1 ⊃ 2 ⊃ 3, 1 ⊃ 4, 5 → 1(이어짐), 3 → 6(이어짐) */
	private static final RelationGraph GRAPH = new RelationGraph(List.of(new Relation(1, 2, RelationKind.CONTAINS),
			new Relation(2, 3, RelationKind.CONTAINS), new Relation(1, 4, RelationKind.CONTAINS),
			new Relation(5, 1, RelationKind.FOLLOWS), new Relation(3, 6, RelationKind.FOLLOWS)));

	@Test
	void findsTheParentTheAncestorsAndAllDescendantsThroughContainmentOnly() {
		assertThat(GRAPH.parentOf(3)).contains(2L);
		assertThat(GRAPH.parentOf(1)).isEmpty();
		// 이어짐은 상위가 아니다
		assertThat(GRAPH.parentOf(6)).isEmpty();
		assertThat(GRAPH.ancestors(3)).containsExactly(2L, 1L);
		assertThat(GRAPH.ancestors(1)).isEmpty();
		assertThat(GRAPH.descendants(1)).containsExactly(2L, 3L, 4L);
		assertThat(GRAPH.descendants(3)).isEmpty();
	}

	@Test
	void tellsWhetherARelationWouldCloseACycleOfItsOwnKind() {
		assertThat(GRAPH.closesCycle(new Relation(3, 1, RelationKind.CONTAINS))).isTrue();
		assertThat(GRAPH.closesCycle(new Relation(2, 1, RelationKind.CONTAINS))).isTrue();
		assertThat(GRAPH.closesCycle(new Relation(4, 3, RelationKind.CONTAINS))).isFalse();
		// 이어짐 5 → 1 뿐이라 1 → 5 는 순환, 3 → 1 은 아니다
		assertThat(GRAPH.closesCycle(new Relation(1, 5, RelationKind.FOLLOWS))).isTrue();
		assertThat(GRAPH.closesCycle(new Relation(3, 1, RelationKind.FOLLOWS))).isFalse();
	}

	@Test
	void findsAndDropsTheRelationOfAPairWhateverItsDirection() {
		assertThat(GRAPH.between(2, 1)).contains(new Relation(1, 2, RelationKind.CONTAINS));
		assertThat(GRAPH.between(2, 4)).isEmpty();
		assertThat(GRAPH.without(2, 1).parentOf(2)).isEmpty();
		assertThat(GRAPH.without(2, 1).relations()).hasSize(4);
	}

	@Test
	void walksOnceEvenWhenTheStoredRelationsAlreadyHoldACycle() {
		RelationGraph broken = new RelationGraph(
				List.of(new Relation(1, 2, RelationKind.CONTAINS), new Relation(2, 1, RelationKind.CONTAINS)));

		assertThat(broken.ancestors(1)).containsExactly(2L);
		assertThat(broken.descendants(1)).containsExactly(2L);
	}

	@Test
	void aRelationNeverJoinsAStorylineToItself() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Relation(7, 7, RelationKind.FOLLOWS))
			.withMessage("스토리라인은 자기 자신과 관계를 갖지 않는다: 7");
		assertThat(new Relation(1, 2, RelationKind.FOLLOWS).connects(2, 1)).isTrue();
		assertThat(new Relation(1, 2, RelationKind.FOLLOWS).touches(3)).isFalse();
	}

	@Test
	void theCriteriaRejectValuesOutsideTheirRanges() {
		Duration window = Duration.ofDays(14);

		assertThat(new RelationCriteria(1.0, 0.0, 2, 1.0, Duration.ofSeconds(1)).minEvents()).isEqualTo(2);
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.0, 0.2, 3, 0.88, window))
			.withMessage("확률 기준은 0 보다 크고 1 이하다: 0.0");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.6, 1.1, 3, 0.88, window))
			.withMessage("한 사안 기준은 0 이상 1 이하다: 1.1");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.6, 0.2, 1, 0.88, window))
			.withMessage("사건 수 하한은 2 이상이다: 1");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.6, 0.2, 3, 1.01, window))
			.withMessage("후보 점수 기준은 0 보다 크고 1 이하다: 1.01");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.6, 0.2, 3, 0.88, Duration.ZERO))
			.withMessageStartingWith("시간 창은 0 보다 큰 초 단위다");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(0.6, 0.2, 3, 0.88, Duration.ofMillis(1500)))
			.withMessageStartingWith("시간 창은 0 보다 큰 초 단위다");
		assertThatIllegalArgumentException().isThrownBy(() -> new RelationCriteria(Double.NaN, 0.2, 3, 0.88, window))
			.withMessageStartingWith("확률 기준은");
	}

	@Test
	void aClaimedRowExpiresAfterTwentyMinutesAndFailuresRunOutAtThree() {
		Instant now = Instant.parse("2026-11-10T00:00:00Z");
		UUID token = UUID.fromString("00000000-0000-0000-0000-00000000000b");

		assertThat(new PendingStoryline(7, 2, 1, null).claim(token, now))
			.isEqualTo(new PendingStoryline(7, 2, 1, new LinkingLease(token, Instant.parse("2026-11-10T00:20:00Z"))));
		assertThat(RelationSchedule.BATCH_SIZE).isEqualTo(4);
		assertThat(RelationSchedule.MAX_PAIRS).isEqualTo(5);
		assertThat(RelationSchedule.failuresExhausted(2)).isFalse();
		assertThat(RelationSchedule.failuresExhausted(3)).isTrue();
		assertThatIllegalArgumentException().isThrownBy(() -> new PendingStoryline(7, -1, 0, null));
	}

}
