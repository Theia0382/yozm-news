package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.linking.Scored;
import net.yozmnews.storyline.domain.relation.RelationDescription.Described;
import org.junit.jupiter.api.Test;

/**
 * 관계를 물을 후보 쌍과 서술 사건 — 함께 가진 사건 · 점수 기준 · 사건 수 하한 · 다시 묻기의 경계 · 순서, 요청의 순서와 판정할 쪽, 서술
 * 사건 고르기.
 * <p>
 * 지키는 규칙: storyline-R27 · storyline-R28 · storyline-R30
 */
class RelationCandidatesTests {

	private static final RelationCriteria CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14));

	private static final Instant T1 = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-02T00:00:00Z");

	@Test
	void sharingPartnersComeFirstByCountThenScoredPartnersByScore() {
		List<Long> partners = RelationCandidates.partners(1, 5, Map.of(20L, 1, 21L, 3, 22L, 3),
				List.of(scored(30, 0.90), scored(31, 0.95), scored(32, 0.95), scored(21, 0.99)),
				counts(20, 21, 22, 30, 31, 32), Map.of(), CRITERIA);

		assertThat(partners).containsExactly(21L, 22L, 20L, 31L, 32L, 30L);
	}

	@Test
	void aScoreBelowTheCandidateScoreIsNotACandidateButItsBoundaryIs() {
		assertThat(RelationCandidates.partners(1, 5, Map.of(), List.of(scored(30, 0.88), scored(31, 0.8799)),
				counts(30, 31), Map.of(), CRITERIA))
			.containsExactly(30L);
		// 사건을 함께 가졌으면 점수가 없어도 후보다
		assertThat(RelationCandidates.partners(1, 5, Map.of(31L, 1), List.of(), counts(31), Map.of(), CRITERIA))
			.containsExactly(31L);
	}

	@Test
	void bothStorylinesNeedTheMinimumNumberOfEvents() {
		assertThat(RelationCandidates.partners(1, 2, Map.of(20L, 2), List.of(), counts(20), Map.of(), CRITERIA)).isEmpty();
		assertThat(RelationCandidates.partners(1, 3, Map.of(20L, 2, 21L, 2), List.of(), Map.of(20L, 2, 21L, 3), Map.of(),
				CRITERIA))
			.containsExactly(21L);
		// 사건 수를 모르는 상대(그새 없어진 스토리라인)와 자기 자신은 후보가 아니다
		assertThat(RelationCandidates.partners(1, 3, Map.of(1L, 3, 99L, 1), List.of(), Map.of(1L, 3), Map.of(), CRITERIA))
			.isEmpty();
	}

	@Test
	void anAskedPairIsAskedAgainWhenTheSmallerSideHasDoubled() {
		// 지금 작은 쪽은 min(8, 상대의 사건 수) 다 — 20: 5 → 물었을 때 3 의 두 배 미만, 21: 6 → 두 배, 22: 물은 적 없음
		assertThat(RelationCandidates.partners(1, 8, Map.of(20L, 1, 21L, 1, 22L, 1), List.of(),
				Map.of(20L, 5, 21L, 6, 22L, 3), Map.of(20L, 3, 21L, 3), CRITERIA))
			.containsExactly(21L, 22L);
		// 점수를 매기기 전에 거르는 물음도 같은 규칙이다
		assertThat(RelationCandidates.askable(8, 5, 3, CRITERIA)).isFalse();
		assertThat(RelationCandidates.askable(8, 6, 3, CRITERIA)).isTrue();
		assertThat(RelationCandidates.askable(8, 3, null, CRITERIA)).isTrue();
		assertThat(RelationCandidates.askable(8, 2, null, CRITERIA)).isFalse();
		assertThat(RelationCandidates.askable(2, 8, null, CRITERIA)).isFalse();
		assertThat(RelationCandidates.askable(8, null, null, CRITERIA)).isFalse();
		assertThat(RelationCandidates.due(null, 3)).isTrue();
		assertThat(RelationCandidates.due(3, 5)).isFalse();
		assertThat(RelationCandidates.due(3, 6)).isTrue();
	}

	@Test
	void theEarlierStorylineIsFirstAndTheSmallerOneIsTheSubject() {
		assertThat(RelationCandidates.firstIs(9, T1, 4, T2)).isTrue();
		assertThat(RelationCandidates.firstIs(4, T2, 9, T1)).isFalse();
		assertThat(RelationCandidates.firstIs(4, T1, 9, T1)).isTrue();
		assertThat(RelationCandidates.firstIs(9, T1, 4, T1)).isFalse();
		assertThat(RelationCandidates.subjectIs(4, 3, 9, 5)).isTrue();
		assertThat(RelationCandidates.subjectIs(4, 5, 9, 3)).isFalse();
		// 같으면 ID 가 큰 쪽
		assertThat(RelationCandidates.subjectIs(9, 3, 4, 3)).isTrue();
		assertThat(RelationCandidates.subjectIs(4, 3, 9, 3)).isFalse();
	}

	@Test
	void describesTheTenLargestEventsInTimeOrderWithSummariesOnTheThreeLargest() {
		// 사건 12개 — ID 1 ~ 12, 기사 수는 ID 1 · 2 가 9, 3 이 7, 나머지는 2(동률). 시각은 ID 가 클수록 이르다
		List<EventView> events = new java.util.ArrayList<>();
		for (int id = 1; id <= 12; id++) {
			int articles = (id <= 2) ? 9 : (id == 3) ? 7 : 2;
			events.add(event(id, articles, T2.minusSeconds(id * 3600L)));
		}

		List<Described> described = RelationDescription.of(events);

		// 동률(기사 2건)에서는 사건 ID 가 작은 쪽이 든다 — 11 · 12 가 빠진다. 싣는 순서는 시각 순(ID 내림차순)
		assertThat(described).extracting(Described::eventId)
			.containsExactly(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);
		assertThat(described.stream().filter(Described::summarized).map(Described::eventId).toList())
			.containsExactly(3L, 2L, 1L);
		assertThat(described.getFirst().at()).isEqualTo(T2.minusSeconds(36000));
	}

	@Test
	void describesAllEventsOfASmallStoryline() {
		assertThat(RelationDescription.of(List.of(event(5, 1, T2), event(4, 1, T1)))).containsExactly(
				new Described(4, T1, true), new Described(5, T2, true));
		assertThat(RelationDescription.of(List.of())).isEmpty();
	}

	private static Scored scored(long id, double score) {
		return new Scored(id, new Score(score, score, 1.0));
	}

	/** 모두 사건 다섯 */
	private static Map<Long, Integer> counts(long... ids) {
		Map<Long, Integer> counts = new java.util.HashMap<>();
		for (long id : ids) {
			counts.put(id, 5);
		}
		return counts;
	}

	private static EventView event(long id, int articles, Instant first) {
		return new EventView(new EventId(id), EventStatus.OPEN, articles, first, first.plusSeconds(60), first);
	}

}
