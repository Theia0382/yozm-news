package net.yozmnews.evaluation.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;
import net.yozmnews.evaluation.domain.tagging.StoredTagJudgement;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRecord;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link TagJudgementRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 원본 응답은 {@code jsonb} 로 쓰고 {@code raw_response::text} 로 꺼낸다 — 공백 · 키 순서는 원문과 다르고 수치는 자릿수를 지킨다.
 * 이 행을 잠그는 다른 쓰기가 없어 지우기는 잠금 순서 없이 한 문장이다.
 */
@Repository
class JdbcTagJudgementRepository implements TagJudgementRepository {

	private final JdbcClient jdbc;

	JdbcTagJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public TagJudgementId insert(TagJudgementRecord record) {
		long id = this.jdbc.sql("""
				insert into article_tag_judgements (article_id, evaluator, model_version, raw_response, input_tokens,
				    judged_at)
				values (:article, :evaluator, :modelVersion, cast(:rawResponse as jsonb), :inputTokens, :judgedAt)
				returning id
				""")
			.param("article", record.articleId().value())
			.param("evaluator", record.evaluator())
			.param("modelVersion", record.modelVersion())
			.param("rawResponse", record.rawResponse())
			.param("inputTokens", record.inputTokens())
			.param("judgedAt", record.judgedAt().atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		return new TagJudgementId(id);
	}

	@Override
	public Optional<StoredTagJudgement> find(TagJudgementId id) {
		return this.jdbc
			.sql("select evaluator, raw_response::text as raw_response from article_tag_judgements where id = :id")
			.param("id", id.value())
			.query((rs, row) -> new StoredTagJudgement(rs.getString("evaluator"), rs.getString("raw_response")))
			.optional();
	}

	@Override
	public Map<TagJudgementId, TagJudgementView> views(Collection<TagJudgementId> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Long[] values = ids.stream().map(TagJudgementId::value).distinct().toArray(Long[]::new);
		Map<TagJudgementId, TagJudgementView> views = new LinkedHashMap<>();
		this.jdbc.sql("""
				select id, article_id, evaluator, model_version, input_tokens, judged_at
				from article_tag_judgements
				where id = any(:ids)
				order by id
				""").param("ids", values).query(JdbcTagJudgementRepository::view).list().forEach(view -> views.put(view.id(), view));
		return views;
	}

	@Override
	public void delete(TagJudgementId id) {
		this.jdbc.sql("delete from article_tag_judgements where id = :id").param("id", id.value()).update();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
		this.jdbc.sql("delete from article_tag_judgements where article_id = any(:ids)").param("ids", ids).update();
	}

	private static TagJudgementView view(ResultSet rs, int row) throws SQLException {
		return new TagJudgementView(new TagJudgementId(rs.getLong("id")), new ArticleId(rs.getLong("article_id")),
				rs.getString("evaluator"), rs.getString("model_version"), rs.getInt("input_tokens"),
				rs.getObject("judged_at", OffsetDateTime.class).toInstant());
	}

}
