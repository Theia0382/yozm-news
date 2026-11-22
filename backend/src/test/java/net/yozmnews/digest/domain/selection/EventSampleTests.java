package net.yozmnews.digest.domain.selection;

import static net.yozmnews.digest.domain.selection.Facts.article;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 사건마다 생성에 넘길 기사 — (사건 ID, 기사 ID) 해시 순으로 앞 5 건, 같은 사건이면 같은 표본.
 * <p>
 * 지키는 규칙: digest-R17(후보 사건의 기사 순서) · digest-R45
 */
class EventSampleTests {

	/**
	 * 기사 101 ~ 108 의 해시 순(부호 없는 64비트) — 시험 밖에서 따로 계산한 값. 사건 7: 105 · 108 · 106 · 104 · 101 · 102 · 107 · 103,
	 * 사건 8: 106 · 107 · 103 · 105 · 104 · 102 · 108 · 101
	 */
	@Test
	void theSampleIsTheFirstFiveByTheHashOfEventAndArticle() {
		assertThat(ids(EventSample.of(event(7, 101, 108)))).containsExactly(101L, 104L, 105L, 106L, 108L);
		// 대조 — 같은 기사라도 사건이 다르면 다른 표본
		assertThat(ids(EventSample.of(event(8, 101, 108)))).containsExactly(103L, 104L, 105L, 106L, 107L);
	}

	@Test
	void theHashIsComparedUnsigned() {
		// 사건 7 의 104(0x86b9…) · 101(0xabbf…)은 부호 있는 비교면 음수라 맨 앞에 선다 — 부호 없는 비교에서는 105(0x32a7…) 뒤
		assertThat(EventSample.hash(7, 105)).isEqualTo(0x32a7ccedb7ba1795L);
		assertThat(EventSample.hash(7, 104)).isEqualTo(0x86b932e4f947b0eeL);
		assertThat(EventSample.hash(8, 101)).isEqualTo(0xfcf6b6e3c0400b3dL);
	}

	@Test
	void anEventWithFewArticlesGivesThemAllInPublishedOrder() {
		CandidateEvent small = new CandidateEvent(facts(7), List.of(), List.of(article(103).published("2026-11-16T12:00:00Z").build(),
				article(101).published("2026-11-16T13:00:00Z").build()));

		assertThat(ids(EventSample.of(small))).containsExactly(103L, 101L);
	}

	@Test
	void aSampledArticleStaysWhenALaterArticleRanksBehindIt() {
		// 사건 7 의 101 ~ 107 표본은 105 · 106 · 104 · 101 · 102 — 108 이 들면 108(둘째 해시)이 102 를 밀어낸다
		assertThat(ids(EventSample.of(event(7, 101, 107)))).containsExactly(101L, 102L, 104L, 105L, 106L);
		assertThat(ids(EventSample.of(event(7, 101, 108)))).containsExactly(101L, 104L, 105L, 106L, 108L);
		// 대조 — 사건 8 에서 108 은 일곱째 해시라 101 ~ 107 의 표본(103 · 104 · 105 · 106 · 107)이 그대로다
		assertThat(ids(EventSample.of(event(8, 101, 107)))).containsExactly(103L, 104L, 105L, 106L, 107L);
		assertThat(ids(EventSample.of(event(8, 101, 108)))).containsExactly(103L, 104L, 105L, 106L, 107L);
		assertThat(EventSample.ARTICLES_PER_EVENT).isEqualTo(5);
	}

	@Test
	void aCandidateEventHoldsItsArticlesInPublishedOrderAndIsNeverEmpty() {
		CandidateEvent event = new CandidateEvent(facts(7), List.of(), List.of(article(103).published("2026-11-16T13:00:00Z").build(),
				article(102).published("2026-11-16T12:00:00Z").build(),
				article(101).published("2026-11-16T13:00:00Z").build()));

		// 발행 시각 순, 같으면 기사 ID 순
		assertThat(ids(event.articles())).containsExactly(102L, 101L, 103L);
		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvent(facts(7), List.of(), List.of()));
	}

	private static CandidateEvent event(long event, long fromArticle, long toArticle) {
		return new CandidateEvent(facts(event), List.of(),
				LongStream.rangeClosed(fromArticle, toArticle).mapToObj(id -> article(id).build()).toList());
	}

	private static EventFacts facts(long event) {
		return new EventFacts(new EventId(event), Set.of("politics.executive"), Set.of(), 3.0, false);
	}

	private static List<Long> ids(List<ArticleFacts> articles) {
		return articles.stream().map(article -> article.id().value()).toList();
	}

}
