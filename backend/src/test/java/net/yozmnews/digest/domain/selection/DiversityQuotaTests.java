package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 다양성 몫 — 구독 행마다 ⌈자리 ÷ 후보가 있는 행 수⌉ 먼저, 남은 자리는 순위대로. 여러 행에 걸친 사건은 남은 몫이 큰 행 하나의 몫만 쓴다.
 * 자리는 사건 수다. 구독 행: economy.markets · politics(행정 · 입법)
 */
class DiversityQuotaTests {

	@Test
	void oneRowCannotTakeEverySeatWhileAnotherRowHasCandidates() {
		// 순위: 정치 넷 다음 시장 둘. 자리 4 · 행 2 → 몫 2 씩
		List<CandidateEvent> ranked = List.of(politics(1), politics(2), politics(3), politics(4), markets(5),
				markets(6));

		assertThat(ids(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 4))).containsExactly(1L, 2L, 5L, 6L);
	}

	@Test
	void seatsLeftByARowWithFewCandidatesAreFilledInRankOrder() {
		// 자리 5 · 행 2 → 몫 3(올림). 시장은 하나뿐이라 남은 자리를 정치가 순위대로 채운다
		List<CandidateEvent> ranked = List.of(politics(1), politics(2), politics(3), politics(4), politics(5),
				markets(6));

		assertThat(ids(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 5)))
			.containsExactly(1L, 2L, 3L, 4L, 6L);
	}

	@Test
	void anEventOnSeveralRowsUsesOneShareOnly() {
		// 7 은 정치 · 시장 둘 다 — 남은 몫이 같으면 앞 행(economy.markets)의 몫. 자리 4 · 몫 2
		CandidateEvent both = Facts.single(Facts.article(7).subtopics("politics.executive", "economy.markets"));
		List<CandidateEvent> ranked = List.of(both, politics(1), politics(2), politics(3), markets(5));

		// 7(시장 몫) · 1 · 2(정치 몫 다 씀) · 5(시장 몫) — 3 은 빠진다
		assertThat(ids(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 4))).containsExactly(7L, 1L, 2L,
				5L);
	}

	@Test
	void theSeatsCountEventsNotArticles() {
		// 사건 1 은 기사 셋, 사건 2 는 기사 하나 — 자리 2 면 사건 둘이 모두 든다
		CandidateEvent threeArticles = Facts.event(1, Facts.article(11), Facts.article(12), Facts.article(13));
		List<CandidateEvent> ranked = List.of(threeArticles, politics(2), politics(3));

		assertThat(ids(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 2))).containsExactly(1L, 2L);
	}

	@Test
	void noSeatsGiveNothingAndFewerCandidatesThanSeatsGiveThemAll() {
		List<CandidateEvent> ranked = List.of(politics(1), markets(2));

		assertThat(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 0)).isEmpty();
		assertThat(ids(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 60))).containsExactly(1L, 2L);
		assertThat(DiversityQuota.apply(List.of(), Facts.politicsAndMarkets(), 60)).isEmpty();
	}

	@Test
	void aRemainderSeatIsKeptByTheCeilingShare() {
		// 자리 3 · 행 2 → 몫 2(올림). 정치 · 시장이 번갈아 순위 — 몫 안에서 1 · 2 · 3 이 먼저, 4 는 자리가 없다
		List<CandidateEvent> ranked = new ArrayList<>(List.of(politics(1), markets(2), politics(3), markets(4)));

		assertThat(ids(DiversityQuota.apply(ranked, Facts.politicsAndMarkets(), 3))).containsExactly(1L, 2L, 3L);
	}

	private static CandidateEvent politics(long id) {
		return Facts.single(Facts.article(id).subtopics("politics.executive"));
	}

	private static CandidateEvent markets(long id) {
		return Facts.single(Facts.article(id).subtopics("economy.markets"));
	}

	private static List<Long> ids(List<CandidateEvent> events) {
		return events.stream().map(event -> event.facts().id().value()).toList();
	}

}
