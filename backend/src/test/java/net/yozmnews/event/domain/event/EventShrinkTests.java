package net.yozmnews.event.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.MatchFeatures;
import org.junit.jupiter.api.Test;

/**
 * 구성 기사를 지운 뒤 사건의 새 값 — 남는 기사가 없으면 지우고, 표현이 있는 기사만 중심 · 핵심 말에서 빼며, 기사 수는 지운 수만큼 줄인다.
 * 사건은 [1,0] · [1,0] · [4,3] 세 기사로 중심 [2,1] 이다.
 * <p>
 * 지키는 규칙: event-R8 · event-R12
 */
class EventShrinkTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final LockedEvent EVENT = new LockedEvent(9, EventStatus.OPEN, "model-a", new float[] { 2, 1 }, 3, T,
			T, T, T);

	@Test
	void removingEveryArticleEmptiesTheEvent() {
		assertThat(EventShrink.of(EVENT, List.of(new ArticleId(1), new ArticleId(2), new ArticleId(3)), Map.of()))
			.isInstanceOf(EventShrink.Emptied.class);
	}

	@Test
	void aRepresentedArticleLeavesTheCentroidAndItsTerms() {
		EventShrink.Shrunk shrunk = (EventShrink.Shrunk) EventShrink.of(EVENT, List.of(new ArticleId(3)),
				Map.of(new ArticleId(3), features(3, new float[] { 4, 3 }, "금리", "환율")));

		// ([2,1] × 3 − [4,3]) / 2 = [1,0]
		assertThat(shrunk.centroid()).containsExactly(1f, 0f);
		assertThat(shrunk.articleCount()).isEqualTo(2);
		assertThat(shrunk.removedTerms()).containsExactly("금리", "환율");
	}

	@Test
	void anArticleWithoutARepresentationOnlyLowersTheCount() {
		EventShrink.Shrunk shrunk = (EventShrink.Shrunk) EventShrink.of(EVENT, List.of(new ArticleId(3)), Map.of());

		assertThat(shrunk.centroid()).containsExactly(2f, 1f);
		assertThat(shrunk.articleCount()).isEqualTo(2);
		assertThat(shrunk.removedTerms()).isEmpty();
	}

	@Test
	void severalArticlesAreRemovedOneAfterAnotherAndTheirTermsAddUp() {
		EventShrink.Shrunk shrunk = (EventShrink.Shrunk) EventShrink.of(EVENT,
				List.of(new ArticleId(1), new ArticleId(3)),
				Map.of(new ArticleId(1), features(1, new float[] { 1, 0 }, "금리"), new ArticleId(3),
						features(3, new float[] { 4, 3 }, "금리", "환율")));

		// ([2,1] × 3 − [1,0]) / 2 = [2.5,1.5], ([2.5,1.5] × 2 − [4,3]) / 1 = [1,0]
		assertThat(shrunk.centroid()).containsExactly(1f, 0f);
		assertThat(shrunk.articleCount()).isEqualTo(1);
		assertThat(shrunk.removedTerms()).containsExactly("금리", "금리", "환율");
	}

	private static MatchFeatures features(long article, float[] vector, String... terms) {
		return new MatchFeatures(new ArticleId(article), "model-a", Embedding.of(vector), List.of(terms), T);
	}

}
