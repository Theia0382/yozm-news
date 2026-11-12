package net.yozmnews.event.domain.tally;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventTally;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;

/**
 * 기간 집계 — 소스는 기사들의 엔트리 소스 합집합, 중요도는 중요도가 있는 기사의 평균(소수 산술), 재료가 없는 기사의 처리, 사건 여럿을 한
 * 묶음으로 셀 때 소스 · 평균을 기사 전체로 세는지.
 * <p>
 * 지키는 규칙: event-R15
 */
class EventTalliesTests {

	private static final ArticleId A1 = new ArticleId(1);

	private static final ArticleId A2 = new ArticleId(2);

	private static final ArticleId A3 = new ArticleId(3);

	private static final ArticleId A4 = new ArticleId(4);

	private static final ArticleId A5 = new ArticleId(5);

	@Test
	void countsDistinctSourcesAndAveragesTheImportancesThatExist() {
		Map<Long, List<ArticleId>> members = new LinkedHashMap<>();
		members.put(10L, List.of(A1, A2, A3));
		Map<ArticleId, Set<SourceId>> sources = Map.of(A1, Set.of(new SourceId(1), new SourceId(2)), A2,
				Set.of(new SourceId(2)), A3, Set.of(new SourceId(3)));
		Map<ArticleId, Double> importances = Map.of(A1, 2.8, A2, 3.2);

		EventTally tally = EventTallies.tally(members, sources, importances).get(10L);

		assertThat(tally.articles()).isEqualTo(3);
		assertThat(tally.sources()).isEqualTo(3);
		assertThat(tally.importance()).isEqualByComparingTo("3.00");
	}

	@Test
	void theAverageIsDecimalSoABoundaryDoesNotSlipBelow() {
		// 1.1 과 4.1 의 평균은 2.6 이다 — 이진 소수 산술이면 2.5999999999999996 이 된다
		EventTally tally = EventTallies.tally(Map.of(11L, List.of(A4, A5)), Map.of(), Map.of(A4, 1.1, A5, 4.1)).get(11L);

		assertThat(tally.importance()).isEqualByComparingTo(new BigDecimal("2.60"));
	}

	@Test
	void aGroupOfEventsIsTalliedOverAllItsArticlesNotPerEvent() {
		// 사건 A 의 기사 둘(소스 1 · 2, 중요도 5 · 5)과 사건 B 의 기사 하나(소스 2 · 3, 중요도 2)를 한 묶음으로 —
		// 사건별 소스 수를 더하면 4, 사건별 평균을 평균하면 3.5 다
		Map<String, List<ArticleId>> members = new LinkedHashMap<>();
		members.put("line", List.of(A1, A2, A3));
		members.put("empty", List.of());
		Map<ArticleId, Set<SourceId>> sources = Map.of(A1, Set.of(new SourceId(1)), A2, Set.of(new SourceId(2)), A3,
				Set.of(new SourceId(2), new SourceId(3)));
		Map<ArticleId, Double> importances = Map.of(A1, 5.0, A2, 5.0, A3, 2.0);

		Map<String, EventTally> tallies = EventTallies.tally(members, sources, importances);

		assertThat(tallies.keySet()).containsExactly("line", "empty");
		assertThat(tallies.get("line").articles()).isEqualTo(3);
		assertThat(tallies.get("line").sources()).isEqualTo(3);
		assertThat(tallies.get("line").importance()).isEqualByComparingTo("4");
		assertThat(tallies.get("empty")).isEqualTo(new EventTally(0, 0, null));
	}

	@Test
	void anArticleWithoutMaterialAddsNothing() {
		// 기사 4 — 소스도 중요도도 없다(지워졌거나 평가 전). 기사 수에는 든다
		Map<Long, EventTally> tallies = EventTallies.tally(Map.of(12L, List.of(A3, A4)),
				Map.of(A3, Set.of(new SourceId(3))), Map.of());

		assertThat(tallies.get(12L)).isEqualTo(new EventTally(2, 1, null));
	}

}
