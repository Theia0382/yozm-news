package net.yozmnews.article.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.article.EntryRows;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 지운 소스 · 피드의 기사 쪽 삭제 — 유스케이스를 같은 스레드에서 불러 커밋된 엔트리 · 기사 행과 발행된 {@link ArticlesRemoved} 를
 * 잰다.
 * <p>
 * 지키는 규칙: article-R15
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class ArticleRemovalTests {

	private static final Instant SEEN = Instant.parse("2026-10-20T00:00:00Z");

	@Autowired
	ArticleRemoval removal;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	EntryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new EntryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void removingASourceDeletesTheArticlesOnlyItHasAndKeepsTheSharedOnes(CapturedOutput output) {
		// 소스 1(피드 11 · 12), 소스 2(피드 21)
		long onlySource1 = this.rows.article();
		this.rows.entry(onlySource1, 1, 11, "NEW", false, null, SEEN);
		this.rows.entry(onlySource1, 1, 12, "TITLE", false, null, SEEN);
		long shared = this.rows.article();
		this.rows.entry(shared, 1, 11, "NEW", false, null, SEEN);
		this.rows.entry(shared, 2, 21, "URL", false, null, SEEN);
		long onlySource2 = this.rows.article();
		this.rows.entry(onlySource2, 2, 21, "NEW", false, null, SEEN);

		this.removal.removeSource(new SourceId(1));
		List<Map<String, Object>> once = entries();
		this.removal.removeSource(new SourceId(1));

		assertThat(articles()).containsExactly(shared, onlySource2);
		assertThat(entries()).isEqualTo(once)
			.containsExactly(Map.of("article_id", shared, "feed_id", 21L), Map.of("article_id", onlySource2, "feed_id", 21L));
		assertThat(output).contains("지운 소스의 엔트리 3건 · 기사 1건을 지웠다 — 소스 1")
			.contains("지운 소스의 엔트리 0건 · 기사 0건을 지웠다 — 소스 1");
	}

	@Test
	void removingAFeedDeletesTheArticlesOnlyItHasAndKeepsTheOtherFeedsEntries(CapturedOutput output) {
		long onlyFeed11 = this.rows.article();
		this.rows.entry(onlyFeed11, 1, 11, "NEW", false, null, SEEN);
		long feeds11And12 = this.rows.article();
		this.rows.entry(feeds11And12, 1, 11, "NEW", false, null, SEEN);
		this.rows.entry(feeds11And12, 1, 12, "TITLE", false, null, SEEN);
		long onlyFeed12 = this.rows.article();
		this.rows.entry(onlyFeed12, 1, 12, "NEW", false, null, SEEN);

		this.removal.removeFeed(new SourceId(1), new FeedId(11));
		List<Map<String, Object>> once = entries();
		this.removal.removeFeed(new SourceId(1), new FeedId(11));

		assertThat(articles()).containsExactly(feeds11And12, onlyFeed12);
		assertThat(entries()).isEqualTo(once)
			.containsExactly(Map.of("article_id", feeds11And12, "feed_id", 12L),
					Map.of("article_id", onlyFeed12, "feed_id", 12L));
		assertThat(output).contains("지운 피드의 엔트리 2건 · 기사 1건을 지웠다 — 소스 1, 피드 11");
	}

	@Test
	void announcesTheDeletedArticlesOnceAndNothingWhenReprocessed() {
		long onlySource1 = this.rows.article();
		this.rows.entry(onlySource1, 1, 11, "NEW", false, null, SEEN);
		long shared = this.rows.article();
		this.rows.entry(shared, 1, 11, "NEW", false, null, SEEN);
		this.rows.entry(shared, 2, 21, "URL", false, null, SEEN);
		long alsoOnlySource1 = this.rows.article();
		this.rows.entry(alsoOnlySource1, 1, 12, "NEW", false, null, SEEN);

		this.removal.removeSource(new SourceId(1));
		this.removal.removeSource(new SourceId(1));

		// 다른 소스의 엔트리가 남은 기사(shared)는 싣지 않는다. 다시 처리한 두 번째 호출은 지운 기사가 없어 발행하지 않는다
		assertThat(removedEvents()).containsExactly(
				new ArticlesRemoved(List.of(new ArticleId(onlySource1), new ArticleId(alsoOnlySource1))));
	}

	@Test
	void announcesTheArticlesOfADeletedFeed() {
		long onlyFeed11 = this.rows.article();
		this.rows.entry(onlyFeed11, 1, 11, "NEW", false, null, SEEN);
		long feeds11And12 = this.rows.article();
		this.rows.entry(feeds11And12, 1, 11, "NEW", false, null, SEEN);
		this.rows.entry(feeds11And12, 1, 12, "TITLE", false, null, SEEN);

		this.removal.removeFeed(new SourceId(1), new FeedId(11));

		assertThat(removedEvents()).containsExactly(new ArticlesRemoved(List.of(new ArticleId(onlyFeed11))));
	}

	@Test
	void splitsALargeDeletionIntoEventsOfAThousandArticles() {
		// 소스 1 만 가진 기사 1,001건을 한 문장으로 넣는다
		List<Long> ids = this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, published_at,
				    published_at_estimated, first_seen_at)
				select 'https://bulk.example/' || g, sha256(convert_to('https://bulk.example/' || g, 'UTF8')), '제목',
				    sha256(convert_to('제목', 'UTF8')), '2026-10-20T00:00:00Z', false, '2026-10-20T00:00:00Z'
				from generate_series(1, 1001) g
				returning id
				""").query(Long.class).list();
		this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				select id, 11, 1, url_hash, '{}', false, 'NEW', '2026-10-20T00:00:00Z' from articles
				""").update();

		this.removal.removeSource(new SourceId(1));

		List<ArticlesRemoved> removed = removedEvents();
		assertThat(removed).extracting(event -> event.articleIds().size()).containsExactly(1000, 1);
		assertThat(removed.stream().flatMap(event -> event.articleIds().stream()).map(ArticleId::value))
			.containsExactlyElementsOf(ids.stream().sorted().toList());
	}

	private List<ArticlesRemoved> removedEvents() {
		return this.events.stream(ArticlesRemoved.class).toList();
	}

	private List<Long> articles() {
		return this.jdbc.sql("select id from articles order by id").query(Long.class).list();
	}

	private List<Map<String, Object>> entries() {
		return this.jdbc.sql("select article_id, feed_id from article_feed_entries order by article_id, feed_id")
			.query()
			.listOfRows();
	}

}
