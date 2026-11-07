package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.AssignmentCriteria;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 배정 기준 이력 — 같은 값은 한 행 · 같은 ID, 값이 하나라도 다르면 새 행, 창은 초로 적는다. Flyway 가 적용된 실제 PostgreSQL 에서
 * 본다.
 * <p>
 * 지키는 규칙: event-R17
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcCriteriaRepository.class })
class JdbcCriteriaRepositoryTests {

	private static final AssignmentCriteria DEFAULTS = new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40,
			0.50);

	@Autowired
	JdbcCriteriaRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void theSameValuesRegisterOnceAndKeepTheFirstTime() {
		long first = this.repository.register(DEFAULTS, Instant.parse("2026-11-06T00:00:00Z"));
		long again = this.repository.register(new AssignmentCriteria(Duration.ofMinutes(4320), 0.80, 0.55, 0.40, 0.50),
				Instant.parse("2026-11-06T01:00:00Z"));

		assertThat(again).isEqualTo(first);
		assertThat(row(first)).containsEntry("window_seconds", 259200L)
			.containsEntry("upper", 0.80)
			.containsEntry("lower", 0.55)
			.containsEntry("store", 0.40)
			.containsEntry("probability", 0.50)
			.containsEntry("created", "2026-11-06T00:00:00Z");
	}

	@Test
	void anyDifferentValueIsANewRow() {
		long base = this.repository.register(DEFAULTS, Instant.parse("2026-11-06T00:00:00Z"));

		assertThat(this.repository.register(new AssignmentCriteria(Duration.ofHours(48), 0.80, 0.55, 0.40, 0.50),
				Instant.parse("2026-11-06T00:00:00Z"))).isNotEqualTo(base);
		assertThat(this.repository.register(new AssignmentCriteria(Duration.ofHours(72), 0.81, 0.55, 0.40, 0.50),
				Instant.parse("2026-11-06T00:00:00Z"))).isNotEqualTo(base);
		assertThat(this.repository.register(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.56, 0.40, 0.50),
				Instant.parse("2026-11-06T00:00:00Z"))).isNotEqualTo(base);
		assertThat(this.repository.register(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.41, 0.50),
				Instant.parse("2026-11-06T00:00:00Z"))).isNotEqualTo(base);
		assertThat(this.repository.register(new AssignmentCriteria(Duration.ofHours(72), 0.80, 0.55, 0.40, 0.51),
				Instant.parse("2026-11-06T00:00:00Z"))).isNotEqualTo(base);
		assertThat(this.jdbc.sql("select count(*) from event_assignment_criteria where id >= :base")
			.param("base", base)
			.query(Long.class)
			.single()).isEqualTo(6L);
	}

	private Map<String, @Nullable Object> row(long id) {
		return this.jdbc.sql("""
				select window_seconds, upper, lower, store, probability,
				    to_char(created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created
				from event_assignment_criteria where id = :id
				""").param("id", id).query().singleRow();
	}

}
