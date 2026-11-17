package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 사건 단위 중복 제거 — 사건마다 대표 하나(자리는 가장 앞 순위), 사건 미배정 기사는 그대로, 큰 슬롯이 쓴 사건은 빠짐, 대표가 없으면 앞 순위 후보
 */
class EventDedupTests {

	private static final ArticleFacts A1 = Facts.article(1).build();

	private static final ArticleFacts A2 = Facts.article(2).build();

	private static final ArticleFacts A3 = Facts.article(3).build();

	private static final ArticleFacts A4 = Facts.article(4).build();

	private static final ArticleFacts A5 = Facts.article(5).build();

	/** 순위: 1 · 2 · 3 · 4 · 5 — 사건 10 은 1 · 3 · 5, 사건 20 은 4, 2 는 미배정 */
	private static final List<ArticleFacts> RANKED = List.of(A1, A2, A3, A4, A5);

	private static final Map<ArticleId, EventId> EVENT_OF = Map.of(new ArticleId(1), new EventId(10),
			new ArticleId(3), new EventId(10), new ArticleId(5), new EventId(10), new ArticleId(4), new EventId(20));

	@Test
	void eachEventKeepsItsRepresentativeAtItsBestRankAndUnassignedArticlesStay() {
		// 사건 10 의 대표는 5 — 자리는 그 사건의 가장 앞 순위(1 의 자리)
		assertThat(EventDedup.apply(RANKED, EVENT_OF, Map.of(new EventId(10), new ArticleId(5),
				new EventId(20), new ArticleId(4)), Set.of()))
			.containsExactly(A5, A2, A4);
	}

	@Test
	void anEventTakenByTheBigSlotIsLeftOut() {
		assertThat(EventDedup.apply(RANKED, EVENT_OF, Map.of(new EventId(10), new ArticleId(5),
				new EventId(20), new ArticleId(4)), Set.of(new EventId(10))))
			.containsExactly(A2, A4);
	}

	@Test
	void withoutARepresentativeTheBestRankedCandidateStands() {
		assertThat(EventDedup.apply(RANKED, EVENT_OF, Map.of(), Set.of())).containsExactly(A1, A2, A4);
	}

	@Test
	void groupsAreTheCandidatesOfEachEventInRankOrder() {
		assertThat(EventDedup.groups(RANKED, EVENT_OF)).containsExactly(
				Map.entry(new EventId(10), List.of(new ArticleId(1), new ArticleId(3), new ArticleId(5))),
				Map.entry(new EventId(20), List.of(new ArticleId(4))));
	}

}
