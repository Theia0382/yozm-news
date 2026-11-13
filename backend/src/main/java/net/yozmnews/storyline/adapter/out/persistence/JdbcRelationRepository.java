package net.yozmnews.storyline.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationMove;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.relation.StoredAsked;
import net.yozmnews.storyline.domain.relation.StoredRelation;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link RelationRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 쌍의 고유는 두 ID 의 작은 쪽 · 큰 쪽으로 본다. 맺기는 그 쌍의 행을 지우고 넣는다. 모든 관계({@code all})는 두 끝이 모두 합쳐지지 않은 행만 낸다.
 * 기준 이력은 같은 값의 행을 먼저 읽고 없을 때만 넣는다. 물은 기록은 {@code INSERT … ON CONFLICT (low_id, high_id) DO UPDATE} 다.
 */
@Repository
class JdbcRelationRepository implements RelationRepository {

	private final JdbcClient jdbc;

	JdbcRelationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Relation> all() {
		return this.jdbc.sql("""
				select r.kind, r.source_id, r.target_id
				from storyline_relations r
				join storylines s on s.id = r.source_id and s.merged_into is null
				join storylines t on t.id = r.target_id and t.merged_into is null
				order by r.kind, r.source_id, r.target_id
				""").query(JdbcRelationRepository::relation).list();
	}

	@Override
	public List<StoredRelation> of(long storylineId) {
		return this.jdbc.sql("""
				select kind, source_id, target_id, probability, judgement_id, criteria_id, linked_at
				from storyline_relations
				where source_id = :id or target_id = :id
				order by kind, source_id, target_id
				""")
			.param("id", storylineId)
			.query((rs, row) -> new StoredRelation(relation(rs, row), rs.getDouble("probability"),
					rs.getLong("judgement_id"), rs.getLong("criteria_id"),
					rs.getObject("linked_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	@Override
	public void link(Relation relation, double probability, long judgementId, long criteriaId, Instant now) {
		unlink(relation.sourceId(), relation.targetId());
		this.jdbc.sql("""
				insert into storyline_relations (kind, source_id, target_id, probability, judgement_id, criteria_id,
				    linked_at)
				values (:kind, :source, :target, :probability, :judgement, :criteria, :now)
				""")
			.param("kind", relation.kind().name())
			.param("source", relation.sourceId())
			.param("target", relation.targetId())
			.param("probability", probability)
			.param("judgement", judgementId)
			.param("criteria", criteriaId)
			.param("now", timestamp(now))
			.update();
	}

	@Override
	public void unlink(long a, long b) {
		this.jdbc.sql("""
				delete from storyline_relations
				where least(source_id, target_id) = :low and greatest(source_id, target_id) = :high
				""").param("low", Math.min(a, b)).param("high", Math.max(a, b)).update();
	}

	@Override
	public Map<Long, Integer> askedAtSmallerEvents(long storylineId) {
		Map<Long, Integer> asked = new LinkedHashMap<>();
		this.jdbc.sql("""
				select case when low_id = :id then high_id else low_id end as partner, smaller_events
				from storyline_relation_asked
				where low_id = :id or high_id = :id
				order by partner
				""")
			.param("id", storylineId)
			.query((rs, row) -> asked.put(rs.getLong("partner"), rs.getInt("smaller_events")))
			.list();
		return asked;
	}

	@Override
	public Map<Long, RelationCriteria> criteria(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, RelationCriteria> found = new LinkedHashMap<>();
		this.jdbc.sql("""
				select id, probability, one_matter, min_events, candidate_score, window_seconds
				from storyline_relation_criteria where id = any(:ids) order by id
				""")
			.param("ids", ids.toArray(Long[]::new))
			.query((rs, row) -> found.put(rs.getLong("id"),
					new RelationCriteria(rs.getDouble("probability"), rs.getDouble("one_matter"), rs.getInt("min_events"),
							rs.getDouble("candidate_score"), Duration.ofSeconds(rs.getLong("window_seconds")))))
			.list();
		return Collections.unmodifiableMap(found);
	}

	@Override
	public List<StoredAsked> askedOf(long storylineId) {
		return this.jdbc.sql("""
				select low_id, high_id, smaller_events, outcome, judgement_id, criteria_id, asked_at
				from storyline_relation_asked
				where low_id = :id or high_id = :id
				order by case when low_id = :id then high_id else low_id end
				""")
			.param("id", storylineId)
			.query((rs, row) -> {
				long judgement = rs.getLong("judgement_id");
				Long judgementId = rs.wasNull() ? null : judgement;
				return new StoredAsked(rs.getLong("low_id"), rs.getLong("high_id"), rs.getInt("smaller_events"),
						RelationOutcome.valueOf(rs.getString("outcome")), judgementId, rs.getLong("criteria_id"),
						rs.getObject("asked_at", OffsetDateTime.class).toInstant());
			})
			.list();
	}

	@Override
	public void recordAsked(long a, long b, int smallerEvents, @Nullable Long judgementId, long criteriaId,
			RelationOutcome outcome, Instant now) {
		this.jdbc.sql("""
				insert into storyline_relation_asked (low_id, high_id, smaller_events, outcome, judgement_id, criteria_id,
				    asked_at)
				values (:low, :high, :smaller, :outcome, :judgement, :criteria, :now)
				on conflict (low_id, high_id) do update
				set smaller_events = excluded.smaller_events, outcome = excluded.outcome,
				    judgement_id = excluded.judgement_id, criteria_id = excluded.criteria_id, asked_at = excluded.asked_at
				""")
			.param("low", Math.min(a, b))
			.param("high", Math.max(a, b))
			.param("smaller", smallerEvents)
			.param("outcome", outcome.name())
			.param("judgement", judgementId, Types.BIGINT)
			.param("criteria", criteriaId)
			.param("now", timestamp(now))
			.update();
	}

	@Override
	public long registerCriteria(RelationCriteria criteria, Instant now) {
		Optional<Long> existing = this.jdbc.sql("""
				select id from storyline_relation_criteria
				where probability = :probability and one_matter = :oneMatter and min_events = :minEvents
				  and candidate_score = :candidateScore and window_seconds = :window
				""")
			.param("probability", criteria.probability())
			.param("oneMatter", criteria.oneMatter())
			.param("minEvents", criteria.minEvents())
			.param("candidateScore", criteria.candidateScore())
			.param("window", criteria.window().toSeconds())
			.query(Long.class)
			.optional();
		if (existing.isPresent()) {
			return existing.get();
		}
		return this.jdbc.sql("""
				insert into storyline_relation_criteria (probability, one_matter, min_events, candidate_score,
				    window_seconds, created_at)
				values (:probability, :oneMatter, :minEvents, :candidateScore, :window, :now)
				on conflict (probability, one_matter, min_events, candidate_score, window_seconds)
				do update set window_seconds = excluded.window_seconds
				returning id
				""")
			.param("probability", criteria.probability())
			.param("oneMatter", criteria.oneMatter())
			.param("minEvents", criteria.minEvents())
			.param("candidateScore", criteria.candidateScore())
			.param("window", criteria.window().toSeconds())
			.param("now", timestamp(now))
			.query(Long.class)
			.single();
	}

	@Override
	public void applyMove(RelationMove.Plan plan, Instant now) {
		// 지우고 나서 옮긴다 — 옮기기는 남은 행의 끝과 시각만 고친다
		for (Relation removed : plan.removed()) {
			unlink(removed.sourceId(), removed.targetId());
		}
		for (RelationMove.Moved moved : plan.moved()) {
			this.jdbc.sql("""
					update storyline_relations set source_id = :source, target_id = :target, linked_at = :now
					where kind = :kind and source_id = :fromSource and target_id = :fromTarget
					""")
				.param("source", moved.to().sourceId())
				.param("target", moved.to().targetId())
				.param("now", timestamp(now))
				.param("kind", moved.from().kind().name())
				.param("fromSource", moved.from().sourceId())
				.param("fromTarget", moved.from().targetId())
				.update();
		}
	}

	@Override
	public void forgetAsked(long storylineId) {
		this.jdbc.sql("delete from storyline_relation_asked where low_id = :id or high_id = :id")
			.param("id", storylineId)
			.update();
	}

	private static Relation relation(ResultSet rs, int row) throws SQLException {
		return new Relation(rs.getLong("source_id"), rs.getLong("target_id"), RelationKind.valueOf(rs.getString("kind")));
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
