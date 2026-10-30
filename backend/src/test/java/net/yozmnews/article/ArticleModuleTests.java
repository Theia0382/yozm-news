package net.yozmnews.article;

import static net.yozmnews.article.Fetched.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
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
 * 지키는 규칙: article-R11
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
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

}
