package net.yozmnews.platform.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundSummary;
import net.yozmnews.platform.domain.RoundRun;
import net.yozmnews.platform.domain.RoundRunRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link RoundRunRepository} 의 PostgreSQL 구현 — docs/specs/platform.md §4 · §6 {@code round_runs}.
 * <p>
 * 최근 기록은 창 안의 합 · 회차 수와 마지막으로 끝난 행(창 밖이어도)을 한 문장으로 읽는다.
 */
@Repository
class JdbcRoundRunRepository implements RoundRunRepository {

	private final JdbcClient jdbc;

	JdbcRoundRunRepository(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public void insert(RoundRun run) {
		this.jdbc.sql("""
				insert into round_runs (round, started_at, ended_at, batches, handled, ended_by)
				values (:round, :started, :ended, :batches, :handled, :end)
				""")
			.param("round", run.round())
			.param("started", utc(run.startedAt()))
			.param("ended", utc(run.endedAt()))
			.param("batches", run.batches())
			.param("handled", run.handled())
			.param("end", run.end().name())
			.update();
	}

	@Override
	public RoundSummary recent(String round, Instant since) {
		return this.jdbc.sql("""
				select w.handled, w.runs, l.started_at, l.ended_at, l.batches, l.handled as last_handled, l.ended_by
				from (select coalesce(sum(handled), 0) as handled, count(*) as runs
				      from round_runs
				      where round = :round and ended_at >= :since) w
				left join lateral (select started_at, ended_at, batches, handled, ended_by
				                   from round_runs
				                   where round = :round
				                   order by ended_at desc, id desc
				                   limit 1) l on true
				""")
			.param("round", round)
			.param("since", utc(since))
			.query((rs, row) -> {
				@Nullable String endedBy = rs.getString("ended_by");
				RoundSummary.@Nullable LastRound last = (endedBy == null) ? null
						: new RoundSummary.LastRound(rs.getObject("started_at", OffsetDateTime.class).toInstant(),
								rs.getObject("ended_at", OffsetDateTime.class).toInstant(), rs.getInt("batches"),
								rs.getInt("last_handled"), RoundEnd.valueOf(endedBy));
				return new RoundSummary(rs.getLong("handled"), rs.getInt("runs"), last);
			})
			.single();
	}

	@Override
	public int deleteEndedBefore(Instant before) {
		return this.jdbc.sql("delete from round_runs where ended_at < :before").param("before", utc(before)).update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
