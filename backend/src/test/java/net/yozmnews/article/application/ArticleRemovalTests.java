package net.yozmnews.article.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
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

/**
 * 지운 소스 · 피드의 기사 쪽 삭제 — 유스케이스를 같은 스레드에서 불러 커밋된 엔트리 · 기사 행을 잰다.
 * <p>
 * 지키는 규칙: article-R15
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class ArticleRemovalTests {

	private static final Instant SEEN = Instant.parse("2026-10-20T00:00:00Z");

	@Autowired
	ArticleRemoval removal;

	@Autowired
	JdbcClient jdbc;

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

	private List<Long> articles() {
		return this.jdbc.sql("select id from articles order by id").query(Long.class).list();
	}

	private List<Map<String, Object>> entries() {
		return this.jdbc.sql("select article_id, feed_id from article_feed_entries order by article_id, feed_id")
			.query()
			.listOfRows();
	}

}
