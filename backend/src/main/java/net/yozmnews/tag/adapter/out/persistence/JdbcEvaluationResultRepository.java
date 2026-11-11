package net.yozmnews.tag.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.tag.EvaluationCursor;
import net.yozmnews.tag.EvaluationId;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.domain.result.BackfillTarget;
import net.yozmnews.tag.domain.result.EvaluationRecord;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.ListedEvaluation;
import net.yozmnews.tag.domain.result.SampleRow;
import net.yozmnews.tag.domain.result.StoredEvaluation;
import net.yozmnews.tag.domain.result.StoredEvaluationDetail;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.review.StoredKeys;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link EvaluationResultRepository} 의 JdbcClient 구현 — docs/specs/tag.md §4.
 * <p>
 * 평가 행은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 태그 행은 평가와 함께 넣고, 빠진 태그 채움이 없는 행만 더한다. 분포는
 * {@code jsonb} 로 쓴다(분포의 키는 형식 키 · 중요도 수준). 원본 응답 · 평가기 · 모델 버전 · 입력 토큰은 판정 원본({@code evaluation})에
 * 있고, 평가 행은 판정 원본 ID 만 갖는다. 지우기는 평가 행을 평가 ID 순으로 잠근 뒤 태그 행 → 평가 행
 * 순이다 — 채움의 잠금 읽기도 평가 ID 순이라 서로 엇갈려 기다리지 않는다.
 * <p>
 * 기간 흘려 받기는 SQL 한 문장을 {@link #STREAM_FETCH_SIZE} 행씩 커서로 받아 평가 ID 가 바뀔 때 한 건을 넘긴다(태그 행은 조회와 같은 순서). PgJDBC 는 autocommit 이
 * 꺼진 연결(트랜잭션 안)에서 fetch size 가 있을 때만 나눠 받고, 아니면 결과 전체를 메모리에 담는다 — 그래서 트랜잭션 밖 호출은 막는다.
 */
@Repository
class JdbcEvaluationResultRepository implements EvaluationResultRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 기간 흘려 받기에서 커서가 한 번에 받는 행 수 */
	static final int STREAM_FETCH_SIZE = 1_000;

	/** 평가 단위로 모아 받는 조회의 열 — 평가 행 {@code e} 와 태그 행 {@code t}. 모으기 하나({@code Streamed})가 읽는다 */
	private static final String COLUMNS = """
			e.id, e.article_id, e.tag_set_id, e.format_key, e.format_probability, e.importance, e.judgement_id,
			       e.evaluated_at, t.tag_kind, t.tag_key, t.probability""";

	private static final TypeReference<Map<String, Double>> FORMAT_DISTRIBUTION = new TypeReference<>() {
	};

	private static final TypeReference<Map<Integer, Double>> IMPORTANCE_DISTRIBUTION = new TypeReference<>() {
	};

	/** 기사의 최신 평가 고르기 — 버전 번호가 큰 것(tag-R15). {@code tag_sets s} 를 이은 조회가 함께 쓰는 정렬 */
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
				insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
				    format_distribution, importance, importance_distribution, evaluated_at)
				values (:article, :version, :judgement, :formatKey, :formatProbability, cast(:formatDistribution as jsonb),
				    :importance, cast(:importanceDistribution as jsonb), :evaluatedAt)
				returning id
				""")
			.param("article", record.articleId().value())
			.param("version", record.tagSetId().value())
			.param("judgement", record.judgementId().value())
			.param("formatKey", record.formatKey())
			.param("formatProbability", record.formatProbability())
			.param("formatDistribution", JSON.writeValueAsString(new TreeMap<>(record.formatDistribution())))
			.param("importance", record.importance(), Types.DOUBLE)
			.param("importanceDistribution", JSON.writeValueAsString(new TreeMap<>(record.importanceDistribution())))
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
	public Map<ArticleId, Double> findLatestImportances(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return Map.of();
		}
		// 기사마다 가장 큰 버전 하나를 먼저 고르고 그 중요도가 없으면 뺀다 — 이전 버전의 값으로 내려가지 않는다
		Map<ArticleId, Double> importances = new LinkedHashMap<>();
		this.jdbc.sql("""
				select distinct on (e.article_id) e.article_id, e.importance from article_evaluations e
				join tag_sets s on s.id = e.tag_set_id
				where e.article_id = any(:ids)
				order by e.article_id, %s
				""".formatted(LATEST_VERSION_FIRST))
			.param("ids", articleIds.stream().map(ArticleId::value).toArray(Long[]::new))
			.query((ResultSet rs) -> {
				Double importance = rs.getObject("importance", Double.class);
				if (importance != null) {
					importances.put(new ArticleId(rs.getLong("article_id")), importance);
				}
			});
		return Collections.unmodifiableMap(importances);
	}

	@Override
	public void streamVersionPeriod(TagSetId version, Instant from, Instant to, Consumer<SampleRow> sink) {
		scan(this.jdbc.sql("""
				select %s
				from article_evaluations e
				left join article_tags t on t.evaluation_id = e.id
				where e.tag_set_id = :version and e.evaluated_at >= :from and e.evaluated_at < :to
				order by e.id, t.tag_kind, t.tag_key
				""".formatted(COLUMNS)).param("version", version.value()).param("from", timestamp(from))
			.param("to", timestamp(to)), true, listed -> {
				sink.accept(listed.sample());
				return true;
			});
	}

	@Override
	public void streamFirstEvaluatedIn(Instant from, Instant to, Consumer<StoredEvaluation> sink) {
		// 첫 평가 시각이 기간 안인 기사 — 같은 시각의 두 평가가 둘 다 남아도 기사 집합이라 한 번이다
		scan(this.jdbc.sql("""
				with firsts as (
				  select c.article_id from article_evaluations c
				  where c.evaluated_at >= :from and c.evaluated_at < :to
				    and not exists (select 1 from article_evaluations p
				                    where p.article_id = c.article_id and p.evaluated_at < c.evaluated_at)
				), latest as (
				  select distinct on (e.article_id) e.id, e.article_id, e.tag_set_id, e.format_key, e.format_probability,
				         e.importance, e.judgement_id, e.evaluated_at
				  from article_evaluations e join tag_sets s on s.id = e.tag_set_id
				  where e.article_id in (select article_id from firsts)
				  order by e.article_id, %s
				)
				select l.*, t.tag_kind, t.tag_key, t.probability from latest l
				left join article_tags t on t.evaluation_id = l.id and t.tag_kind = 'TOPIC'
				order by l.article_id, t.tag_kind, t.tag_key
				""".formatted(LATEST_VERSION_FIRST)).param("from", timestamp(from)).param("to", timestamp(to)), true,
				listed -> {
					sink.accept(listed.sample().evaluation());
					return true;
				});
	}

	@Override
	public void scanNewestFirst(TagSetId version, Instant from, Instant to, @Nullable EvaluationCursor after,
			@Nullable StoredKeys required, @Nullable String formatKey, Predicate<ListedEvaluation> sink) {
		StringBuilder where = new StringBuilder(
				"e.tag_set_id = :version and e.evaluated_at >= :from and e.evaluated_at < :to");
		if (after != null) {
			where.append(" and (e.evaluated_at, e.id) < (:afterAt, :afterId)");
		}
		if (required != null) {
			// 거르기의 필요조건 — 기본 키 (evaluation_id, tag_kind, tag_key)로 찾는다
			where.append(" and exists (select 1 from article_tags k where k.evaluation_id = e.id"
					+ " and k.tag_kind = :requiredKind and k.tag_key = any(:requiredKeys))");
		}
		if (formatKey != null) {
			where.append(" and e.format_key = :formatKey");
		}
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				select %s
				from article_evaluations e
				left join article_tags t on t.evaluation_id = e.id
				where %s
				order by e.evaluated_at desc, e.id desc, t.tag_kind, t.tag_key
				""".formatted(COLUMNS, where))
			.param("version", version.value())
			.param("from", timestamp(from))
			.param("to", timestamp(to));
		if (after != null) {
			statement = statement.param("afterAt", timestamp(after.evaluatedAt())).param("afterId", after.id().value());
		}
		if (required != null) {
			statement = statement.param("requiredKind", required.kind().name())
				.param("requiredKeys", required.keys().toArray(String[]::new));
		}
		if (formatKey != null) {
			statement = statement.param("formatKey", formatKey);
		}
		scan(statement, true, sink);
	}

	@Override
	public void streamIds(TagSetId version, Instant from, Instant to, LongConsumer sink) {
		requireTransaction();
		this.jdbc.sql("""
				select id from article_evaluations
				where tag_set_id = :version and evaluated_at >= :from and evaluated_at < :to
				""")
			.param("version", version.value())
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.withFetchSize(STREAM_FETCH_SIZE)
			.query((ResultSet rs) -> {
				sink.accept(rs.getLong("id"));
			});
	}

	@Override
	public List<ListedEvaluation> findListed(Collection<EvaluationId> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		List<ListedEvaluation> listed = new ArrayList<>(ids.size());
		scan(this.jdbc.sql("""
				select %s
				from article_evaluations e
				left join article_tags t on t.evaluation_id = e.id
				where e.id = any(:ids)
				order by e.id, t.tag_kind, t.tag_key
				""".formatted(COLUMNS)).param("ids", ids.stream().map(EvaluationId::value).toArray(Long[]::new)), false,
				evaluation -> listed.add(evaluation));
		return List.copyOf(listed);
	}

	@Override
	public Optional<StoredEvaluationDetail> findDetail(EvaluationId id) {
		return this.jdbc.sql("""
				select e.id, e.article_id, e.tag_set_id, e.format_key, e.importance, e.judgement_id, e.evaluated_at,
				       e.format_distribution::text as format_distribution,
				       e.importance_distribution::text as importance_distribution
				from article_evaluations e
				where e.id = :id
				""").param("id", id.value()).query((rs, row) -> {
					StoredEvaluation evaluation = new StoredEvaluation(new ArticleId(rs.getLong("article_id")),
							new TagSetId(rs.getLong("tag_set_id")), rs.getString("format_key"),
							rs.getObject("importance", Double.class), tags(id.value()));
					return new StoredEvaluationDetail(id, instant(rs), new TagJudgementId(rs.getLong("judgement_id")),
							JSON.readValue(rs.getString("format_distribution"), FORMAT_DISTRIBUTION),
							JSON.readValue(rs.getString("importance_distribution"), IMPORTANCE_DISTRIBUTION),
							evaluation);
				}).optional();
	}

	/**
	 * 평가 행과 그 태그 행을 평가마다 모아 한 건씩 넘긴다 — 행은 평가 ID 가 이어지게 정렬돼 있어야 한다({@link #COLUMNS}). sink 가 거짓을
	 * 돌려주면 남은 행을 읽지 않고 멈춘다.
	 * @param cursor 커서로 나눠 받는가 — 참이면 트랜잭션 안에서만 부른다
	 * @throws IllegalStateException 커서로 받는데 트랜잭션 밖에서 불렀을 때
	 */
	private static void scan(JdbcClient.StatementSpec statement, boolean cursor, Predicate<ListedEvaluation> sink) {
		if (cursor) {
			requireTransaction();
			statement = statement.withFetchSize(STREAM_FETCH_SIZE);
		}
		// 끝까지 읽었는가 — JdbcClient 는 추출 결과가 null 이면 실패로 본다
		statement.query((ResultSet rs) -> {
			Streamed current = new Streamed();
			while (rs.next()) {
				long id = rs.getLong("id");
				if (!current.holds(id)) {
					if (!current.flush(sink)) {
						return Boolean.FALSE;
					}
					current.start(id, rs);
				}
				if (rs.getString("tag_kind") != null) {
					current.tags.add(tag(rs, 0));
				}
			}
			return current.flush(sink);
		});
	}

	@Override
	public List<BackfillTarget> lockForBackfill(Instant from, Instant to, long afterId, int limit) {
		return this.jdbc.sql("""
				select id, article_id, tag_set_id, judgement_id
				from article_evaluations
				where evaluated_at >= :from and evaluated_at < :to and id > :after
				order by id
				limit :limit
				for key share
				""")
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.param("after", afterId)
			.param("limit", limit)
			.query((rs, row) -> new BackfillTarget(rs.getLong("id"), new ArticleId(rs.getLong("article_id")),
					new TagSetId(rs.getLong("tag_set_id")), new TagJudgementId(rs.getLong("judgement_id"))))
			.list();
	}

	@Override
	public int addMissingTags(long evaluationId, ArticleId articleId, List<StoredTag> tags) {
		int added = 0;
		for (StoredTag tag : tags) {
			added += this.jdbc.sql("""
					insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
					values (:evaluation, :article, :kind, :key, :probability)
					on conflict (evaluation_id, tag_kind, tag_key) do nothing
					""")
				.param("evaluation", evaluationId)
				.param("article", articleId.value())
				.param("kind", tag.kind().name())
				.param("key", tag.key())
				.param("probability", tag.probability())
				.update();
		}
		return added;
	}

	@Override
	public Map<TagSetId, Long> countByVersion(Instant from, Instant to) {
		Map<TagSetId, Long> counts = new LinkedHashMap<>();
		this.jdbc.sql("""
				select tag_set_id, count(*) as evaluations from article_evaluations
				where evaluated_at >= :from and evaluated_at < :to
				group by tag_set_id order by tag_set_id
				""")
			.param("from", timestamp(from))
			.param("to", timestamp(to))
			.query(rs -> {
				counts.put(new TagSetId(rs.getLong("tag_set_id")), rs.getLong("evaluations"));
			});
		return counts;
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
		// 채움과 같은 평가 ID 순으로 먼저 잠근다 — 채움이 그 사이에 태그 행을 더해 평가 행 삭제가 외래 키에 걸리지 않게
		this.jdbc.sql("select id from article_evaluations where article_id = any(:ids) order by id for update")
			.param("ids", ids)
			.query(Long.class)
			.list();
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

	private static Instant instant(ResultSet rs) throws SQLException {
		return rs.getObject("evaluated_at", OffsetDateTime.class).toInstant();
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

		private long judgementId;

		private @Nullable Instant evaluatedAt;

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
			this.judgementId = rs.getLong("judgement_id");
			this.evaluatedAt = instant(rs);
			this.tags.clear();
		}

		/** 담은 평가를 넘긴다 — 담은 것이 없으면 참. sink 의 답을 돌려준다 */
		boolean flush(Predicate<ListedEvaluation> sink) {
			if (this.article == null) {
				return true;
			}
			SampleRow sample = new SampleRow(new StoredEvaluation(this.article,
					Objects.requireNonNull(this.version, "tag_set_id"),
					Objects.requireNonNull(this.formatKey, "format_key"), this.importance, this.tags),
					this.formatProbability, new TagJudgementId(this.judgementId));
			this.article = null;
			return sink.test(new ListedEvaluation(new EvaluationId(this.id),
					Objects.requireNonNull(this.evaluatedAt, "evaluated_at"), sample));
		}

	}

}
