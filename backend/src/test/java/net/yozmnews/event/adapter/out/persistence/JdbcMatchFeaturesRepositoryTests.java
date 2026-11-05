package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.MatchFeatures;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 기사 표현 저장소 — 벡터의 텍스트 표현이 실제 pgvector 에 들어가는지, (기사, 모델 버전)마다 한 행 · 먼저 쓴 것이 남음, 차원이 다른 모델
 * 버전의 공존, 핵심 말 배열, 지우기를 Flyway 가 적용된 실제 PostgreSQL 에서 본다. 벡터는 {@code embedding::text} 로 되읽은 리터럴과
 * 비교한다.
 * <p>
 * 지키는 규칙: event-R4 · event-R8
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcMatchFeaturesRepository.class })
class JdbcMatchFeaturesRepositoryTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	@Autowired
	JdbcMatchFeaturesRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void insertsTheVectorEntitiesAndTime() {
		boolean inserted = this.repository.insertIfAbsent(features(1, "model-a", new float[] { 0.25f, -0.5f, 1.0E-5f },
				List.of("한국은행", "금리")));

		assertThat(inserted).isTrue();
		assertThat(row(1, "model-a")).containsEntry("embedding", "[0.25,-0.5,1e-05]")
			.containsEntry("entities", "{한국은행,금리}")
			.containsEntry("created", "2026-11-05T00:00:00Z");
	}

	@Test
	void theFirstRowOfAnArticleAndModelVersionStays() {
		this.repository.insertIfAbsent(features(2, "model-a", new float[] { 1f, 0f }, List.of("금리")));

		boolean second = this.repository.insertIfAbsent(features(2, "model-a", new float[] { 0f, 1f }, List.of("동결")));

		assertThat(second).isFalse();
		assertThat(row(2, "model-a")).containsEntry("embedding", "[1,0]").containsEntry("entities", "{금리}");
	}

	@Test
	void modelVersionsWithOtherDimensionsLiveSideBySide() {
		this.repository.insertIfAbsent(features(3, "model-a", new float[] { 1f, 0f, 0f }, List.of()));
		this.repository.insertIfAbsent(features(3, "model-b", new float[] { 0f, 1f }, List.of()));

		assertThat(row(3, "model-a")).containsEntry("embedding", "[1,0,0]");
		assertThat(row(3, "model-b")).containsEntry("embedding", "[0,1]");
	}

	@Test
	void noEntitiesAreStoredAsAnEmptyArray() {
		this.repository.insertIfAbsent(features(4, "model-a", new float[] { 1f }, List.of()));

		assertThat(row(4, "model-a")).containsEntry("entities", "{}");
	}

	@Test
	void deletesEveryModelVersionOfTheGivenArticlesOnly() {
		this.repository.insertIfAbsent(features(5, "model-a", new float[] { 1f }, List.of()));
		this.repository.insertIfAbsent(features(5, "model-b", new float[] { 1f }, List.of()));
		this.repository.insertIfAbsent(features(6, "model-a", new float[] { 1f }, List.of()));

		this.repository.deleteByArticles(List.of(new ArticleId(5)));
		this.repository.deleteByArticles(List.of());

		assertThat(this.jdbc.sql("select article_id from article_match_features").query(Long.class).list())
			.containsExactly(6L);
	}

	private static MatchFeatures features(long article, String model, float[] vector, List<String> entities) {
		return new MatchFeatures(new ArticleId(article), model, Embedding.of(vector), entities, T);
	}

	private Map<String, @Nullable Object> row(long article, String model) {
		return this.jdbc.sql("""
				select embedding::text as embedding, entities::text as entities,
				    to_char(created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created
				from article_match_features where article_id = :article and model_version = :model
				""").param("article", article).param("model", model).query().singleRow();
	}

}
