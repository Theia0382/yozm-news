package net.yozmnews.platform.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.domain.ClockJumps;
import net.yozmnews.platform.domain.MeasurementClockRows;
import net.yozmnews.platform.domain.MeasurementClockState;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link MeasurementClockRows} 의 PostgreSQL 구현 — docs/specs/platform.md §4.
 * <p>
 * 시계 행은 하나뿐이다(키 1). 넘긴 기록은 넘기기 전 시각 순으로 읽는다.
 */
@Repository
class JdbcMeasurementClockRows implements MeasurementClockRows {

	private final JdbcClient jdbc;

	JdbcMeasurementClockRows(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public boolean exists() {
		return this.jdbc.sql("select exists (select 1 from measurement_clock)").query(Boolean.class).single();
	}

	@Override
	public Optional<MeasurementClockState> load() {
		Optional<Row> row = this.jdbc.sql("select started_at, anchored_at from measurement_clock where id = 1")
			.query((rs, n) -> new Row(rs.getObject("started_at", OffsetDateTime.class).toInstant(),
					instant(rs.getObject("anchored_at", OffsetDateTime.class))))
			.optional();
		if (row.isEmpty()) {
			return Optional.empty();
		}
		List<ClockJumps.Jump> jumps = this.jdbc
			.sql("select from_at, to_at from measurement_clock_jumps where clock_id = 1 order by from_at, id")
			.query((rs, n) -> new ClockJumps.Jump(rs.getObject("from_at", OffsetDateTime.class).toInstant(),
					rs.getObject("to_at", OffsetDateTime.class).toInstant()))
			.list();
		return Optional.of(new MeasurementClockState(row.get().startedAt(), row.get().anchoredAt(), new ClockJumps(jumps)));
	}

	@Override
	public void anchor(Instant realNow) {
		this.jdbc.sql("update measurement_clock set anchored_at = :realNow where id = 1 and anchored_at is null")
			.param("realNow", realNow.atOffset(ZoneOffset.UTC))
			.update();
	}

	@Override
	public void addJump(ClockJumps.Jump jump) {
		this.jdbc.sql("insert into measurement_clock_jumps (clock_id, from_at, to_at) values (1, :from, :to)")
			.param("from", jump.from().atOffset(ZoneOffset.UTC))
			.param("to", jump.to().atOffset(ZoneOffset.UTC))
			.update();
	}

	private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
		return (value == null) ? null : value.toInstant();
	}

	private record Row(Instant startedAt, @Nullable Instant anchoredAt) {
	}

}
