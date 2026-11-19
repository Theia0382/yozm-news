package net.yozmnews;

import java.time.Instant;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 측정 profile 시험 컨텍스트의 시계 행 — 마이그레이션 직후, 시계 bean 이 만들어지기 전에 측정용 시계 행(출발 시각 {@link #STARTED})을 넣는다.
 * 컨텍스트마다 새 데이터베이스라 다른 시험에 새지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MeasurementClockRow {

	/** 측정 시각의 출발점 — 2026-11-19 02:00 MST(America/Denver, UTC−7). 시험이 그 뒤 시각으로 넘긴다 */
	public static final Instant STARTED = Instant.parse("2026-11-19T09:00:00Z");

	@Bean
	FlywayMigrationStrategy migrateThenInsertTheMeasurementClockRow() {
		return flyway -> {
			flyway.migrate();
			new JdbcTemplate(flyway.getConfiguration().getDataSource()).update(
					"insert into measurement_clock (started_at) values (?::timestamptz)", STARTED.toString());
		};
	}

}
