package net.yozmnews.event.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.feature.Embedding;
import net.yozmnews.event.domain.feature.MatchFeatures;
import net.yozmnews.event.domain.feature.MatchFeaturesRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link MatchFeaturesRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 넣기는 {@code INSERT … ON CONFLICT (article_id, model_version) DO NOTHING}. 벡터는 {@link VectorText} 의 텍스트 표현을 SQL 이
 * {@code vector} 로 바꾸고 {@code embedding::text} 로 되읽는다. 핵심 말은 {@code text[]} 다. 정리는 지울 행을 (기사, 모델 버전) 순
 * {@code FOR UPDATE SKIP LOCKED} 로 골라 지운다.
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
	public Optional<MatchFeatures> find(ArticleId articleId, String modelVersion) {
		return findAll(List.of(articleId), modelVersion).stream().findFirst();
	}

	@Override
	public List<MatchFeatures> findAll(Collection<ArticleId> articleIds, String modelVersion) {
		if (articleIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.sql("""
				select article_id, model_version, embedding::text as embedding, entities, created_at
				from article_match_features
				where article_id = any(:ids) and model_version = :model
				order by article_id
				""")
			.param("ids", articleIds.stream().map(ArticleId::value).toArray(Long[]::new))
			.param("model", modelVersion)
			.query(JdbcMatchFeaturesRepository::row)
			.list();
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

	@Override
	public int deleteOfClosedEvents(int limit) {
		return this.jdbc.sql("""
				delete from article_match_features f
				where (f.article_id, f.model_version) in (
				    select g.article_id, g.model_version from article_match_features g
				    join event_articles a on a.article_id = g.article_id
				    join events e on e.id = a.event_id
				    where e.status = 'CLOSED'
				    order by g.article_id, g.model_version
				    limit :limit
				    for update of g skip locked)
				""").param("limit", limit).update();
	}

	@Override
	public int deleteUnassigned(Instant createdBefore, int limit) {
		return this.jdbc.sql("""
				delete from article_match_features f
				where (f.article_id, f.model_version) in (
				    select g.article_id, g.model_version from article_match_features g
				    where g.created_at < :before
				      and not exists (
				          select 1 from event_articles a join events e on e.id = a.event_id
				          where a.article_id = g.article_id and e.model_version = g.model_version and e.status = 'OPEN')
				    order by g.article_id, g.model_version
				    limit :limit
				    for update of g skip locked)
				""").param("before", createdBefore.atOffset(ZoneOffset.UTC)).param("limit", limit).update();
	}

	private static MatchFeatures row(ResultSet rs, int row) throws SQLException {
		return new MatchFeatures(new ArticleId(rs.getLong("article_id")), rs.getString("model_version"),
				Embedding.of(VectorText.parse(rs.getString("embedding"))),
				List.of((String[]) rs.getArray("entities").getArray()),
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

}
