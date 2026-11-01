package net.yozmnews.article;

import static net.yozmnews.article.Fetched.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedDeleted;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceDeleted;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;

/**
 * article 모듈 하나의 결선 — source 가 발행한 {@code ArticleFetched} 를 비동기 리스너가 받아 저장하고
 * {@link ArticleStored} 를 발행하는지 Modulith 시나리오로 본다.
 * <p>
 * {@code SourceDeleted} · {@code FeedDeleted} 를 받아 그 소스 · 피드만 가진 기사를 지우는지도 본다. source 는 띄우지 않으므로 피드
 * 확인은 대역({@link AllFeedsExist})이 답한다.
 * <p>
 * 지키는 규칙: article-R11 · article-R15
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, AllFeedsExist.class })
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleModuleTests {

	@Autowired
	JdbcClient jdbc;

	@Test
	void storesAFetchedArticleAndPublishesArticleStored(Scenario scenario) {
		ArticleRows rows = new ArticleRows(this.jdbc);
		rows.deleteAll();

		scenario.publish(item(1, 11, "https://a.example/news/1", null, "국회 본회의 통과",
				Instant.parse("2026-10-20T00:00:00Z")))
			.andWaitForEventOfType(ArticleStored.class)
			.toArriveAndVerify((stored) -> assertThat(rows.articleIds()).containsExactly(stored.articleId().value()));
	}

	@Test
	void removesTheArticlesOfADeletedSource(Scenario scenario) {
		EntryRows entries = new EntryRows(this.jdbc);
		entries.deleteAll();
		long kept = entries.article();
		entries.entry(kept, 2, 21, "NEW", false, null, Instant.parse("2026-10-20T00:00:00Z"));
		long deleted = entries.article();
		entries.entry(deleted, 1, 11, "NEW", false, null, Instant.parse("2026-10-20T00:00:00Z"));
		ArticleRows rows = new ArticleRows(this.jdbc);

		scenario.publish(new SourceDeleted(new SourceId(1)))
			.andWaitForStateChange(rows::articleIds, (ids) -> ids.size() == 1)
			.andVerify((ids) -> assertThat(ids).containsExactly(kept));
	}

	@Test
	void removesTheArticlesOfADeletedFeed(Scenario scenario) {
		EntryRows entries = new EntryRows(this.jdbc);
		entries.deleteAll();
		long kept = entries.article();
		entries.entry(kept, 1, 12, "NEW", false, null, Instant.parse("2026-10-20T00:00:00Z"));
		long deleted = entries.article();
		entries.entry(deleted, 1, 11, "NEW", false, null, Instant.parse("2026-10-20T00:00:00Z"));
		ArticleRows rows = new ArticleRows(this.jdbc);

		scenario.publish(new FeedDeleted(new SourceId(1), new FeedId(11)))
			.andWaitForStateChange(rows::articleIds, (ids) -> ids.size() == 1)
			.andVerify((ids) -> assertThat(ids).containsExactly(kept));
	}

}
