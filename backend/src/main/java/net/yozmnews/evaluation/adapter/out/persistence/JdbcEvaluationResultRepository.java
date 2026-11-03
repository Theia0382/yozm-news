package net.yozmnews.evaluation.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.overview.EvaluationSample;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.result.StoredTag;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link EvaluationResultRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 평가 행과 태그 행은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 분포 · 원본 응답은 {@code jsonb} 로 쓴다(분포의 키는 형식 키 ·
 * 중요도 수준). 지우기는 태그 행 → 평가 행 순이다.
 * <p>
 * 기간 흘려 받기는 SQL 한 문장을 {@link #STREAM_FETCH_SIZE} 행씩 커서로 받아 평가 ID 가 바뀔 때 한 건을 넘긴다(태그 행은 조회와 같은 순서). PgJDBC 는 autocommit 이
 * 꺼진 연결(트랜잭션 안)에서 fetch size 가 있을 때만 나눠 받고, 아니면 결과 전체를 메모리에 담는다 — 그래서 트랜잭션 밖 호출은 막는다.
 */
@Repository
class JdbcEvaluationResultRepository implements EvaluationResultRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 기간 흘려 받기에서 커서가 한 번에 받는 행 수 */
	static final int STREAM_FETCH_SIZE = 1_000;

	/** 기사의 최신 평가 고르기 — 버전 번호가 큰 것(evaluation-R15). {@code tag_sets s} 를 이은 조회가 함께 쓰는 정렬 */
	private static final String LATEST_VERSION_FIRST = "s.number desc";

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
				join tag_sets s on s.id = e.tag_set_id
				where e.article_id = :article
				order by %s
				limit 1
				""".formatted(LATEST_VERSION_FIRST)).param("article", articleId.value()).query((rs, row) -> {
					long id = rs.getLong("id");
					return new StoredEvaluation(articleId, new TagSetId(rs.getLong("tag_set_id")), rs.getString("format_key"),
							rs.getObject("importance", Double.class), tags(id));
				}).optional();
	}

	@Override
	public void streamVersionPeriod(TagSetId version, Instant from, Instant to, Consumer<EvaluationSample> sink) {
		stream(this.jdbc.sql("""
				select e.id, e.article_id, e.tag_set_id, e.format_key, e.format_probability, e.importance, e.input_tokens,
				       t.tag_kind, t.tag_key, t.probability
				from article_evaluations e
				left join article_tags t on t.evaluation_id = e.id
				where e.tag_set_id = :version and e.evaluated_at >= :from and e.evaluated_at < :to
				order by e.id, t.tag_kind, t.tag_key
				""").param("version", version.value()).param("from", timestamp(from)).param("to", timestamp(to)), sink);
	}

	@Override
	public void streamFirstEvaluatedIn(Instant from, Instant to, Consumer<StoredEvaluation> sink) {
		// 첫 평가 시각이 기간 안인 기사 — 같은 시각의 두 평가가 둘 다 남아도 기사 집합이라 한 번이다
		stream(this.jdbc.sql("""
				with firsts as (
				  select c.article_id from article_evaluations c
				  where c.evaluated_at >= :from and c.evaluated_at < :to
				    and not exists (select 1 from article_evaluations p
				                    where p.article_id = c.article_id and p.evaluated_at < c.evaluated_at)
				), latest as (
				  select distinct on (e.article_id) e.id, e.article_id, e.tag_set_id, e.format_key, e.format_probability,
				         e.importance, e.input_tokens
				  from article_evaluations e join tag_sets s on s.id = e.tag_set_id
				  where e.article_id in (select article_id from firsts)
				  order by e.article_id, %s
				)
				select l.*, t.tag_kind, t.tag_key, t.probability from latest l
				left join article_tags t on t.evaluation_id = l.id and t.tag_kind = 'TOPIC'
				order by l.article_id, t.tag_kind, t.tag_key
				""".formatted(LATEST_VERSION_FIRST)).param("from", timestamp(from)).param("to", timestamp(to)),
				sample -> sink.accept(sample.evaluation()));
	}

	/**
	 * 평가 행과 그 태그 행을 평가마다 모아 한 건씩 넘긴다 — 행은 평가 ID 가 이어지게 정렬돼 있어야 한다.
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때
	 */
	private static void stream(JdbcClient.StatementSpec statement, Consumer<EvaluationSample> sink) {
		requireTransaction();
		Streamed current = new Streamed();
		statement.withFetchSize(STREAM_FETCH_SIZE).query(rs -> {
			long id = rs.getLong("id");
			if (!current.holds(id)) {
				current.flush(sink);
				current.start(id, rs);
			}
			if (rs.getString("tag_kind") != null) {
				current.tags.add(tag(rs, 0));
			}
		});
		current.flush(sink);
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

	private static void requireTransaction() {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("기간 흘려 받기는 트랜잭션 안에서 부른다 — 밖에서는 결과 전체가 메모리에 담긴다");
		}
	}

	/** 흘려 받는 중인 평가 한 건 — 평가 ID 가 바뀌면 넘긴다 */
	private static final class Streamed {

		private long id;

		private @Nullable TagSetId version;

		/** 담은 평가가 없으면 null */
		private @Nullable ArticleId article;

		private @Nullable String formatKey;

		private double formatProbability;

		private @Nullable Double importance;

		private int inputTokens;

		private final List<StoredTag> tags = new ArrayList<>();

		boolean holds(long id) {
			return this.article != null && this.id == id;
		}

		void start(long id, ResultSet rs) throws SQLException {
			this.id = id;
			this.article = new ArticleId(rs.getLong("article_id"));
			this.version = new TagSetId(rs.getLong("tag_set_id"));
			this.formatKey = rs.getString("format_key");
			this.formatProbability = rs.getDouble("format_probability");
			this.importance = rs.getObject("importance", Double.class);
			this.inputTokens = rs.getInt("input_tokens");
			this.tags.clear();
		}

		void flush(Consumer<EvaluationSample> sink) {
			if (this.article == null) {
				return;
			}
			sink.accept(new EvaluationSample(new StoredEvaluation(this.article,
					Objects.requireNonNull(this.version, "tag_set_id"),
					Objects.requireNonNull(this.formatKey, "format_key"), this.importance, this.tags),
					this.formatProbability, this.inputTokens));
			this.article = null;
		}

	}

}
