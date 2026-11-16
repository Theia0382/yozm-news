package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;

/**
 * 헤드라인의 출처 이름 엮기 — 엔트리 순서, 같은 이름은 한 번, 이름이 없거나 이름 조회에 없는 피드는 뺀다.
 * <p>
 * 지키는 규칙: article-R17
 */
class HeadlineSourcesTests {

	@Test
	void namesFollowTheEntryOrderOnceEachAndSkipFeedsWithoutAName() {
		// 엔트리 순서 7 · 5 · 6 · 8 · 9 — 피드 ID 순과 다르다. 7 과 9 는 같은 이름, 6 은 이름 없음, 8 은 이름 조회에 없다
		List<ArticleEntryView> entries = List.of(entry(7), entry(5), entry(6), entry(8), entry(9));
		Map<FeedId, FeedLabel> labels = Map.of(new FeedId(5), new FeedLabel("경향신문", "경제"), new FeedId(6),
				new FeedLabel(null, "사회"), new FeedId(7), new FeedLabel("한겨레", "정치"), new FeedId(9),
				new FeedLabel("한겨레", "국제"));

		assertThat(HeadlineSources.names(entries, labels)).containsExactly("한겨레", "경향신문");
	}

	@Test
	void anArticleWithoutNamedFeedsHasNoNames() {
		assertThat(HeadlineSources.names(List.of(entry(6)), Map.of(new FeedId(6), new FeedLabel(null, null))))
			.isEmpty();
		assertThat(HeadlineSources.names(List.of(), Map.of())).isEmpty();
	}

	@Test
	void theFeedsAreCollectedOnceInTheOrderTheyAppear() {
		assertThat(HeadlineSources.feeds(List.of(List.of(entry(7), entry(5)), List.of(entry(5), entry(6)))))
			.containsExactly(new FeedId(7), new FeedId(5), new FeedId(6));
	}

	private static ArticleEntryView entry(long feed) {
		return new ArticleEntryView(new SourceId(3), new FeedId(feed), List.of());
	}

}
