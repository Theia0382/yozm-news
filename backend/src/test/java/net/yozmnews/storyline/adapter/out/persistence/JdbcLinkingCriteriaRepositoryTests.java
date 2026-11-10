package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 잇기 기준 이력 — 같은 값이면 같은 ID, 다른 값이면 새 ID. 창은 초로 적는다.
 * <p>
 * 지키는 규칙: storyline-R14
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcLinkingCriteriaRepository.class })
class JdbcLinkingCriteriaRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	@Autowired
	JdbcLinkingCriteriaRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void theSameValuesShareOneRow() {
		long first = this.repository.register(new LinkingCriteria(Duration.ofDays(14), 0.65), NOW);
		long again = this.repository.register(new LinkingCriteria(Duration.ofDays(14), 0.65), NOW.plusSeconds(60));
		long other = this.repository.register(new LinkingCriteria(Duration.ofDays(14), 0.70), NOW);

		assertThat(again).isEqualTo(first);
		assertThat(other).isNotEqualTo(first);
		assertThat(this.jdbc.sql("select window_seconds from storyline_linking_criteria where id = :id")
			.param("id", first)
			.query(Long.class)
			.single()).isEqualTo(1_209_600L);
	}

}
