package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 같은 사안의 조각 위에 상위를 열 때 그 상위에 들 사건 — 잇기의 규칙 그대로다.
 * <p>
 * 기준: 띠 0.84 ~ 0.88 · 확률 0.70.
 */
class OpeningSelectionTests {

	private static final LinkingCriteria CRITERIA = new LinkingCriteria(Duration.ofDays(14), 0.84, 0.88, 0.70);

	@Test
	void anEventAtOrAboveTheUpperThresholdPassesWithoutBeingAsked() {
		List<Scored> events = List.of(scored(1, 0.88), scored(2, 0.95), scored(3, 0.8799));

		assertThat(OpeningSelection.toAsk(events, CRITERIA)).extracting(Scored::id).containsExactly(3L);
		assertThat(OpeningSelection.passed(events, Map.of(), CRITERIA))
			.containsExactly(new OpeningSelection.Passed(scored(1, 0.88), null),
					new OpeningSelection.Passed(scored(2, 0.95), null));
	}

	@Test
	void anEventInTheBandPassesOnlyWithAProbabilityAtOrAboveTheThreshold() {
		List<Scored> events = List.of(scored(1, 0.84), scored(2, 0.86), scored(3, 0.87), scored(4, 0.85), scored(5, 0.8399));
		Map<Long, Asked> asked = Map.of(1L, new Asked(0.70, 11L), 2L, new Asked(0.69, 12L), 3L, Asked.UNANSWERED);

		// 띠의 아래 경계는 띠 안, 그 아래는 묻지도 들지도 않는다
		assertThat(OpeningSelection.toAsk(events, CRITERIA)).extracting(Scored::id).containsExactly(3L, 2L, 4L, 1L);
		// 확률 경계 포함 · 확률 없는 결과와 물은 결과가 없는 띠 사건은 들지 않는다
		assertThat(OpeningSelection.passed(events, asked, CRITERIA))
			.containsExactly(new OpeningSelection.Passed(scored(1, 0.84), new Asked(0.70, 11L)));
	}

	@Test
	void asksAtMostTwentyBandEventsByScoreThenId() {
		List<Scored> events = new ArrayList<>();
		for (long id = 1; id <= 25; id++) {
			events.add(scored(id, (id <= 5) ? 0.85 : 0.86));
		}

		List<Scored> toAsk = OpeningSelection.toAsk(events, CRITERIA);

		assertThat(OpeningSelection.MAX_ASKED_EVENTS).isEqualTo(20);
		assertThat(toAsk).hasSize(20);
		assertThat(toAsk).extracting(Scored::id).doesNotContain(1L, 2L, 3L, 4L, 5L).startsWith(6L, 7L);
	}

	@Test
	void aParentOpensWithTwoEventsThatAreNotExactlyOneFragment() {
		Set<Long> first = Set.of(1L, 2L, 3L);
		Set<Long> second = Set.of(4L, 5L, 6L);

		assertThat(OpeningSelection.openable(Set.of(1L, 4L), first, second)).isTrue();
		assertThat(OpeningSelection.openable(Set.of(1L, 2L), first, second)).isTrue();
		assertThat(OpeningSelection.openable(Set.of(1L), first, second)).isFalse();
		assertThat(OpeningSelection.openable(Set.of(), first, second)).isFalse();
		// 한 조각의 구성 사건과 같은 집합이면 정돈이 그 상위를 그 조각과 합친다 — 열지 않는다
		assertThat(OpeningSelection.openable(Set.of(1L, 2L, 3L), first, second)).isFalse();
		assertThat(OpeningSelection.openable(Set.of(4L, 5L, 6L), first, second)).isFalse();
		assertThat(OpeningSelection.openable(Set.of(1L, 2L, 3L, 4L), first, second)).isTrue();
	}

	private static Scored scored(long id, double score) {
		return new Scored(id, new Score(score, score, 0));
	}

}
