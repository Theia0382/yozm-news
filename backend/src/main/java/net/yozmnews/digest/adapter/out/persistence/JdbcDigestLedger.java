package net.yozmnews.digest.adapter.out.persistence;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestLedger;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.platform.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DigestLedger} 의 JdbcClient 구현 — {@code digests} 의 날짜 · 기간 조회. docs/specs/digest.md §4
 */
@Repository
class JdbcDigestLedger implements DigestLedger {

	private final JdbcClient jdbc;

	JdbcDigestLedger(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<UserId, LocalDate> latestDates(Collection<UserId> users) {
		if (users.isEmpty()) {
			return Map.of();
		}
		Map<UserId, LocalDate> dates = new LinkedHashMap<>();
		this.jdbc.sql("""
				select user_id, max(digest_date) as latest from digests
				where user_id = any(:users)
				group by user_id
				order by user_id
				""")
			.param("users", users.stream().map(UserId::value).distinct().toArray(Long[]::new))
			.query((ResultSet rs) -> {
				dates.put(new UserId(rs.getLong("user_id")), rs.getObject("latest", LocalDate.class));
			});
		return Collections.unmodifiableMap(dates);
	}

	@Override
	public Optional<LocalDate> latestDate(UserId user) {
		return this.jdbc.sql("select digest_date from digests where user_id = :user order by digest_date desc limit 1")
			.param("user", user.value())
			.query((rs, n) -> rs.getObject(1, LocalDate.class))
			.optional();
	}

	@Override
	public Optional<Instant> lastCoveredEnd(UserId user) {
		return this.jdbc.sql("select period_end from digests where user_id = :user and " + CoveringDigestSql.STATUS
				+ " order by period_end desc limit 1")
			.param("user", user.value())
			.query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant())
			.optional();
	}

	@Override
	public Optional<DigestPeriod> latestPeriodOn(UserId user, LocalDate date) {
		return this.jdbc.sql("""
				select period_start, period_end from digests where user_id = :user and digest_date = :date
				order by id desc limit 1
				""")
			.param("user", user.value())
			.param("date", date)
			.query((rs, n) -> new DigestPeriod(rs.getObject("period_start", OffsetDateTime.class).toInstant(),
					rs.getObject("period_end", OffsetDateTime.class).toInstant()))
			.optional();
	}

	@Override
	public boolean inProgress(UserId user) {
		return this.jdbc.sql("""
				select exists (select 1 from digests where user_id = :user and status in ('SCHEDULED', 'RUNNING'))
				""").param("user", user.value()).query(Boolean.class).single();
	}

	@Override
	public int manualCountBetween(UserId user, Instant from, Instant to) {
		return this.jdbc.sql("""
				select count(*) from digests
				where user_id = :user and trigger = 'MANUAL' and created_at >= :from and created_at < :to
				""")
			.param("user", user.value())
			.param("from", from.atOffset(ZoneOffset.UTC))
			.param("to", to.atOffset(ZoneOffset.UTC))
			.query(Integer.class)
			.single();
	}

	@Override
	public List<DigestId> stalledScheduled(Instant before, int limit) {
		return this.jdbc.sql("""
				select id from digests where status = 'SCHEDULED' and dispatched_at < :before
				order by dispatched_at, id limit :limit
				""")
			.param("before", before.atOffset(ZoneOffset.UTC))
			.param("limit", limit)
			.query((rs, n) -> new DigestId(rs.getLong(1)))
			.list();
	}

	@Override
	public List<DigestId> stalledRunning(Instant before, int limit) {
		return this.jdbc.sql("""
				select id from digests where status = 'RUNNING' and started_at < :before
				order by started_at, id limit :limit
				""")
			.param("before", before.atOffset(ZoneOffset.UTC))
			.param("limit", limit)
			.query((rs, n) -> new DigestId(rs.getLong(1)))
			.list();
	}

}
