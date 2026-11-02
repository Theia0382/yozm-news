package net.yozmnews.evaluation.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.result.StoredTag;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link EvaluationResultRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 평가 행과 태그 행은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 분포 · 원본 응답은 {@code jsonb} 로 쓴다(분포의 키는 형식 키 ·
 * 중요도 수준). 지우기는 태그 행 → 평가 행 순이다.
 */
@Repository
class JdbcEvaluationResultRepository implements EvaluationResultRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcClient jdbc;

	JdbcEvaluationResultRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean exists(ArticleId articleId, TagSetId tagSetId) {
		return this.jdbc.sql("""
				select exists (select 1 from article_evaluations where article_id = :article and tag_set_id = :version)
				""").param("article", articleId.value()).param("version", tagSetId.value()).query(Boolean.class).single();
	}

	@Override
	public void insert(EvaluationRecord record) {
		long evaluationId = this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, evaluator, model_version, format_key,
				    format_probability, format_distribution, importance, importance_distribution, raw_response,
				    input_tokens, evaluated_at)
				values (:article, :version, :evaluator, :modelVersion, :formatKey, :formatProbability,
				    cast(:formatDistribution as jsonb), :importance, cast(:importanceDistribution as jsonb),
				    cast(:rawResponse as jsonb), :inputTokens, :evaluatedAt)
				returning id
				""")
			.param("article", record.articleId().value())
			.param("version", record.tagSetId().value())
			.param("evaluator", record.evaluator())
			.param("modelVersion", record.modelVersion())
			.param("formatKey", record.formatKey())
			.param("formatProbability", record.formatProbability())
			.param("formatDistribution", JSON.writeValueAsString(new TreeMap<>(record.formatDistribution())))
			.param("importance", record.importance(), Types.DOUBLE)
			.param("importanceDistribution", JSON.writeValueAsString(new TreeMap<>(record.importanceDistribution())))
			.param("rawResponse", record.rawResponse())
			.param("inputTokens", record.inputTokens())
			.param("evaluatedAt", timestamp(record.evaluatedAt()))
			.query(Long.class)
			.single();
		for (StoredTag tag : record.tags()) {
			this.jdbc.sql("""
					insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
					values (:evaluation, :article, :kind, :key, :probability)
					""")
				.param("evaluation", evaluationId)
				.param("article", record.articleId().value())
				.param("kind", tag.kind().name())
				.param("key", tag.key())
				.param("probability", tag.probability())
				.update();
		}
	}

	@Override
	public Optional<StoredEvaluation> findLatest(ArticleId articleId) {
		return this.jdbc.sql("""
				select e.id, e.tag_set_id, e.format_key, e.importance from article_evaluations e
				join tag_sets t on t.id = e.tag_set_id
				where e.article_id = :article
				order by t.number desc
				limit 1
				""").param("article", articleId.value()).query((rs, row) -> {
					long id = rs.getLong("id");
					return new StoredEvaluation(articleId, new TagSetId(rs.getLong("tag_set_id")), rs.getString("format_key"),
							rs.getObject("importance", Double.class), tags(id));
				}).optional();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
		this.jdbc.sql("delete from article_tags where article_id = any(:ids)").param("ids", ids).update();
		this.jdbc.sql("delete from article_evaluations where article_id = any(:ids)").param("ids", ids).update();
	}

	private List<StoredTag> tags(long evaluationId) {
		return this.jdbc.sql("""
				select tag_kind, tag_key, probability from article_tags where evaluation_id = :evaluation
				order by tag_kind, tag_key
				""").param("evaluation", evaluationId).query(JdbcEvaluationResultRepository::tag).list();
	}

	private static StoredTag tag(ResultSet rs, int row) throws SQLException {
		return new StoredTag(MultiTagKind.valueOf(rs.getString("tag_kind")), rs.getString("tag_key"),
				rs.getDouble("probability"));
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
