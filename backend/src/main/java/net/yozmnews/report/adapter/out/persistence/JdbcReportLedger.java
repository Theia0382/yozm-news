package net.yozmnews.report.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportPeriod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ReportLedger} 의 PostgreSQL 구현 — {@code reports}. docs/specs/report.md §4
 */
@Repository
class JdbcReportLedger implements ReportLedger {

	private final JdbcClient jdbc;

	JdbcReportLedger(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public Map<UserId, Instant> latestEnds(Collection<UserId> users, ReportPeriodKind kind) {
		Objects.requireNonNull(kind, "kind");
		if (users.isEmpty()) {
			return Map.of();
		}
		return this.jdbc.sql("""
				select user_id, max(period_end) from reports where user_id = any(:users) and period_kind = :kind
				group by user_id""")
			.param("users", users.stream().map(UserId::value).distinct().toArray(Long[]::new))
			.param("kind", kind.name())
			.query((rs, n) -> Map.entry(new UserId(rs.getLong(1)), rs.getObject(2, OffsetDateTime.class).toInstant()))
			.list()
			.stream()
			.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@Override
	public Optional<Instant> latestEnd(UserId user, ReportPeriodKind kind) {
		return this.jdbc.sql("""
				select period_end from reports where user_id = :user and period_kind = :kind
				order by period_end desc limit 1""")
			.param("user", user.value())
			.param("kind", kind.name())
			.query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant())
			.optional();
	}

	@Override
	public List<Weekly> weekliesOverlapping(UserId user, Instant start, Instant end) {
		return this.jdbc.sql("""
				select id, period_start, period_end, status from reports
				where user_id = :user and period_kind = 'WEEK' and period_start < :end and period_end > :start
				order by period_start, id""")
			.param("user", user.value())
			.param("start", start.atOffset(ZoneOffset.UTC))
			.param("end", end.atOffset(ZoneOffset.UTC))
			.query((rs, n) -> new Weekly(new ReportId(rs.getLong("id")),
					new ReportPeriod(ReportPeriodKind.WEEK, rs.getObject("period_start", OffsetDateTime.class).toInstant(),
							rs.getObject("period_end", OffsetDateTime.class).toInstant()),
					ReportStatus.valueOf(rs.getString("status"))))
			.list();
	}


	@Override
	public List<ReportId> stalledScheduled(Instant before, int limit) {
		return this.jdbc.sql("""
				select id from reports where status = 'SCHEDULED' and dispatched_at < :before
				order by dispatched_at, id limit :limit""")
			.param("before", before.atOffset(ZoneOffset.UTC))
			.param("limit", limit)
			.query((rs, n) -> new ReportId(rs.getLong(1)))
			.list();
	}

	@Override
	public List<ReportId> stalledRunning(Instant before, int limit) {
		return this.jdbc.sql("""
				select id from reports where status = 'RUNNING' and started_at < :before
				order by started_at, id limit :limit""")
			.param("before", before.atOffset(ZoneOffset.UTC))
			.param("limit", limit)
			.query((rs, n) -> new ReportId(rs.getLong(1)))
			.list();
	}

}
