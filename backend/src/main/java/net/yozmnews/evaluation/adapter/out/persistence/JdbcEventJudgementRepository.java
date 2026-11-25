package net.yozmnews.evaluation.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 넣기 · 지우기는 스토리라인 소속 표와 함께 쓰는
 * {@link MembershipJudgementRows} 이고, 물은 후보의 ID 키는 {@code eventId} 다. 기간 읽기는 {@code (judged_at, id)} 인덱스를 거꾸로
 * 훑고, ID 로 읽기는 기본 키로 찾는다.
 */
@Repository
class JdbcEventJudgementRepository implements EventJudgementRepository {

	private static final String TABLE = "article_event_judgements";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final TypeReference<List<StoredCandidate>> CANDIDATES = new TypeReference<>() {
	};

	private final JdbcClient jdbc;

	JdbcEventJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public EventJudgementId insert(MembershipRecord record) {
		return new EventJudgementId(MembershipJudgementRows.insert(this.jdbc, TABLE, "eventId", record));
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
	public Optional<EventJudgementView> find(EventJudgementId id) {
		return this.jdbc.sql("""
				select id, article_id, evaluator, model_version, wording_version, candidates::text as candidates,
				       input_tokens, judged_at
				from article_event_judgements
				where id = :id
				""").param("id", id.value()).query(JdbcEventJudgementRepository::view).optional();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		MembershipJudgementRows.deleteByArticles(this.jdbc, TABLE, articleIds);
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

	/** jsonb 의 후보 한 칸을 읽는 모양 {@code eventId} · {@code articleIds} · {@code probability} — 쓰기는 {@link MembershipJudgementRows} */
	private record StoredCandidate(long eventId, List<Long> articleIds, double probability) {
	}

}
