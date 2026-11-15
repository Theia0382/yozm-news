package net.yozmnews.user.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import net.yozmnews.user.domain.guard.LimitKey;
import net.yozmnews.user.domain.guard.LimitWindow;
import net.yozmnews.user.domain.guard.RequestCounts;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link RequestCounts} 의 JdbcClient 구현 — docs/specs/user.md §4 · user-R39 · user-R42.
 * <p>
 * 더하기는 {@code INSERT … ON CONFLICT DO UPDATE … RETURNING} 한 문장이다 — 충돌한 행을 그 문장 동안만 잠그고, 여러 프로세스가 같은 키 ·
 * 창에 더해도 빠지지 않는다.
 */
@Repository
class JdbcRequestCounts implements RequestCounts {

	private final JdbcClient jdbc;

	JdbcRequestCounts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int increment(LimitKey key, LimitWindow window) {
		return this.jdbc.sql("""
				insert into request_limit_counts (limit_key, window_start, window_end, requests)
				values (:key, :start, :end, 1)
				on conflict (limit_key, window_start) do update set requests = request_limit_counts.requests + 1
				returning requests
				""")
			.param("key", key.digest())
			.param("start", Timestamp.from(window.start()))
			.param("end", Timestamp.from(window.end()))
			.query(Integer.class)
			.single();
	}

	@Override
	public int forgetEndedBefore(Instant cutoff) {
		return this.jdbc.sql("delete from request_limit_counts where window_end < :cutoff")
			.param("cutoff", Timestamp.from(cutoff))
			.update();
	}

}
