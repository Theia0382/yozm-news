package net.yozmnews.event.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import net.yozmnews.event.domain.assignment.AssignmentCriteria;
import net.yozmnews.event.domain.assignment.CriteriaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link CriteriaRepository} 의 JdbcClient 구현 — docs/specs/event.md §4.
 * <p>
 * 회차마다 부르므로 같은 값의 행을 먼저 읽고, 없을 때만 넣는다. 넣기는 아무것도 바꾸지 않는 갱신({@code ON CONFLICT … DO UPDATE})으로 ID
 * 를 받는다 — 동시에 같은 값을 넣어도 한 행이고, 둘 다 그 ID 를 받는다. 창은 초로 적는다({@link AssignmentCriteria} 가 초 단위만 받는다).
 */
@Repository
class JdbcCriteriaRepository implements CriteriaRepository {

	private final JdbcClient jdbc;

	JdbcCriteriaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public long register(AssignmentCriteria criteria, Instant now) {
		Optional<Long> existing = this.jdbc.sql("""
				select id from event_assignment_criteria
				where window_seconds = :window and upper = :upper and lower = :lower and store = :store
				  and probability = :probability
				""")
			.param("window", criteria.window().toSeconds())
			.param("upper", criteria.upper())
			.param("lower", criteria.lower())
			.param("store", criteria.store())
			.param("probability", criteria.probability())
			.query(Long.class)
			.optional();
		if (existing.isPresent()) {
			return existing.get();
		}
		return this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (:window, :upper, :lower, :store, :probability, :now)
				on conflict (window_seconds, upper, lower, store, probability)
				do update set window_seconds = excluded.window_seconds
				returning id
				""")
			.param("window", criteria.window().toSeconds())
			.param("upper", criteria.upper())
			.param("lower", criteria.lower())
			.param("store", criteria.store())
			.param("probability", criteria.probability())
			.param("now", now.atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
	}

}
