package net.yozmnews.storyline.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link LinkingCriteriaRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 같은 값의 행을 먼저 읽고, 없을 때만 {@code INSERT … ON CONFLICT … DO UPDATE … RETURNING id} 로 넣는다 — 동시에 같은 값을 넣어도
 * 한 행 · 같은 ID 다. 창은 초로 적는다.
 */
@Repository
class JdbcLinkingCriteriaRepository implements LinkingCriteriaRepository {

	private final JdbcClient jdbc;

	JdbcLinkingCriteriaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public long register(LinkingCriteria criteria, Instant now) {
		Optional<Long> existing = this.jdbc.sql("""
				select id from storyline_linking_criteria where window_seconds = :window and threshold = :threshold
				""")
			.param("window", criteria.window().toSeconds())
			.param("threshold", criteria.threshold())
			.query(Long.class)
			.optional();
		if (existing.isPresent()) {
			return existing.get();
		}
		return this.jdbc.sql("""
				insert into storyline_linking_criteria (window_seconds, threshold, created_at)
				values (:window, :threshold, :now)
				on conflict (window_seconds, threshold) do update set window_seconds = excluded.window_seconds
				returning id
				""")
			.param("window", criteria.window().toSeconds())
			.param("threshold", criteria.threshold())
			.param("now", now.atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
	}

}
