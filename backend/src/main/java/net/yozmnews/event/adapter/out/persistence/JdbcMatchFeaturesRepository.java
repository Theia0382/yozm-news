package net.yozmnews.event.adapter.out.persistence;

import java.time.ZoneOffset;
import java.util.Collection;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link MatchFeaturesRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 넣기는 {@code INSERT … ON CONFLICT (article_id, model_version) DO NOTHING}. 벡터는 {@link VectorText} 의 텍스트 표현을 SQL 이
 * {@code vector} 로 바꾸고, 핵심 말은 {@code text[]} 다.
 */
@Repository
class JdbcMatchFeaturesRepository implements MatchFeaturesRepository {

	private final JdbcClient jdbc;

	JdbcMatchFeaturesRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean insertIfAbsent(MatchFeatures features) {
		return this.jdbc.sql("""
				insert into article_match_features (article_id, model_version, embedding, entities, created_at)
				values (:article, :model, cast(:embedding as vector), :entities, :created)
				on conflict (article_id, model_version) do nothing
				""")
			.param("article", features.articleId().value())
			.param("model", features.modelVersion())
			.param("embedding", VectorText.of(features.embedding()))
			.param("entities", features.entities().toArray(String[]::new))
			.param("created", features.createdAt().atOffset(ZoneOffset.UTC))
			.update() == 1;
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		this.jdbc.sql("delete from article_match_features where article_id = any(:ids)")
			.param("ids", articleIds.stream().map(ArticleId::value).toArray(Long[]::new))
			.update();
	}

}
