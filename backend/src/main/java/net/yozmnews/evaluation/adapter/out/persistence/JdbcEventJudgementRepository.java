package net.yozmnews.evaluation.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link EventJudgementRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 물은 후보와 원본 응답은 {@code jsonb} 로 쓴다. 이 행을 잠그는 다른
 * 쓰기가 없어 지우기는 잠금 순서 없이 한 문장이다. 기간 읽기는 {@code (judged_at, id)} 인덱스를 거꾸로 훑는다.
 */
@Repository
class JdbcEventJudgementRepository implements EventJudgementRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final TypeReference<List<StoredCandidate>> CANDIDATES = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	JdbcEventJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public EventJudgementId insert(MembershipRecord record) {
		long id = this.jdbc.sql("""
				insert into article_event_judgements (article_id, evaluator, model_version, wording_version, candidates,
				    raw_response, input_tokens, judged_at)
				values (:article, :evaluator, :modelVersion, :wordingVersion, cast(:candidates as jsonb),
				    cast(:rawResponse as jsonb), :inputTokens, :judgedAt)
				returning id
				""")
			.param("article", record.articleId().value())
			.param("evaluator", record.evaluator())
			.param("modelVersion", record.modelVersion())
			.param("wordingVersion", record.wordingVersion())
			.param("candidates", candidates(record.candidates()))
			.param("rawResponse", record.rawResponse())
			.param("inputTokens", record.inputTokens())
			.param("judgedAt", record.judgedAt().atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		return new EventJudgementId(id);
	}

	@Override
	public List<EventJudgementView> page(Instant from, Instant to, @Nullable Instant afterJudgedAt,
			@Nullable Long afterId, int limit) {
		if ((afterJudgedAt == null) != (afterId == null)) {
			throw new IllegalArgumentException("커서의 판정 시각과 ID 는 함께 준다");
		}
		String after = (afterJudgedAt == null) ? "" : "and (judged_at, id) < (:afterJudgedAt, :afterId)";
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				select id, article_id, evaluator, model_version, wording_version, candidates::text as candidates,
				       input_tokens, judged_at
				from article_event_judgements
				where judged_at >= :from and judged_at < :to %s
				order by judged_at desc, id desc
				limit :limit
				""".formatted(after))
			.param("from", from.atOffset(ZoneOffset.UTC))
			.param("to", to.atOffset(ZoneOffset.UTC))
			.param("limit", limit);
		if (afterJudgedAt != null) {
			statement = statement.param("afterJudgedAt", afterJudgedAt.atOffset(ZoneOffset.UTC))
				.param("afterId", Objects.requireNonNull(afterId));
		}
		return statement.query(JdbcEventJudgementRepository::view).list();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
		this.jdbc.sql("delete from article_event_judgements where article_id = any(:ids)").param("ids", ids).update();
	}

	/** 물은 후보의 jsonb — 후보마다 {@link StoredCandidate} 한 칸, 요청 순서 */
	private static String candidates(List<JudgedEvent> candidates) {
		return JSON.writeValueAsString(candidates.stream()
			.map(candidate -> new StoredCandidate(candidate.eventId(),
					candidate.describedArticles().stream().map(ArticleId::value).toList(), candidate.probability()))
			.toList());
	}

	private static EventJudgementView view(ResultSet rs, int row) throws SQLException {
		List<JudgedEvent> candidates = JSON.readValue(rs.getString("candidates"), CANDIDATES)
			.stream()
			.map(candidate -> new JudgedEvent(candidate.eventId(),
					candidate.articleIds().stream().map(ArticleId::new).toList(), candidate.probability()))
			.toList();
		return new EventJudgementView(new EventJudgementId(rs.getLong("id")), new ArticleId(rs.getLong("article_id")),
				candidates, rs.getString("evaluator"), rs.getString("model_version"), rs.getString("wording_version"),
				rs.getInt("input_tokens"), rs.getObject("judged_at", OffsetDateTime.class).toInstant());
	}

	/** jsonb 의 후보 한 칸 — 쓰기와 읽기가 함께 쓰는 저장 모양 {@code eventId} · {@code articleIds} · {@code probability} */
	private record StoredCandidate(long eventId, List<Long> articleIds, double probability) {
	}

}
