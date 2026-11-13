package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.storyline.BigStorylineCriteriaRows;
import net.yozmnews.platform.ChangeActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 큰 판정 기준 저장소 — 처음 값은 한 번만 넣고 이력 한 행을 남기는지, {@code NUMERIC} 왕복, 바꾸기와 이력 순서, 값이 없을 때의
 * 잠금을 Flyway 가 적용된 실제 PostgreSQL 에서 본다. 시험은 시험 트랜잭션 안에서 돌고 되돌린다 — 시작에 기준 행을 비운다.
 * <p>
 * 지키는 규칙: storyline-R20
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcBigStorylineCriteriaRepository.class })
class JdbcBigStorylineCriteriaRepositoryTests {

	private static final Instant SEEDED = Instant.parse("2026-11-07T00:00:00Z");

	@Autowired
	JdbcBigStorylineCriteriaRepository repository;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void startFromEmptyTables() {
		BigStorylineCriteriaRows.removeAll(this.jdbc);
	}

	@Test
	void seedsOnceWithOneSystemHistoryRow() {
		BigStorylineCriteria initial = new BigStorylineCriteria(new BigDecimal("15.00"));

		assertThat(this.repository.seedIfAbsent(initial, SEEDED)).isTrue();
		assertThat(this.repository.seedIfAbsent(new BigStorylineCriteria(new BigDecimal("45.00")), SEEDED.plusSeconds(1)))
			.isFalse();

		assertThat(this.repository.current()).contains(initial);
		assertThat(this.repository.changes())
			.containsExactly(new BigStorylineCriteriaChange(null, initial, ChangeActor.SYSTEM, SEEDED));
	}

	@Test
	void theIntensityRoundTripsAtTwoDecimals() {
		this.repository.seedIfAbsent(new BigStorylineCriteria(new BigDecimal("17.5")), SEEDED);

		BigStorylineCriteria read = this.repository.current().orElseThrow();
		assertThat(read.minIntensity()).hasToString("17.50");
		assertThat(this.jdbc.sql("select min_intensity::text from storyline_big_criteria").query(String.class).single())
			.isEqualTo("17.50");
	}

	@Test
	void changingWritesTheValueAndAHistoryRowListedNewestFirst() {
		BigStorylineCriteria initial = new BigStorylineCriteria(new BigDecimal("15.00"));
		BigStorylineCriteria first = new BigStorylineCriteria(new BigDecimal("20.00"));
		BigStorylineCriteria second = new BigStorylineCriteria(new BigDecimal("25.00"));
		this.repository.seedIfAbsent(initial, SEEDED);
		// 둘째 변경을 첫째와 같은 시각에 쓴다 — 나중에 넣은 것이 먼저다
		this.repository.change(this.repository.lockCurrent(), first, ChangeActor.OPERATOR, SEEDED.plusSeconds(60));
		this.repository.change(this.repository.lockCurrent(), second, ChangeActor.OPERATOR, SEEDED.plusSeconds(60));

		assertThat(this.repository.current()).contains(second);
		assertThat(this.repository.changes()).containsExactly(
				new BigStorylineCriteriaChange(first, second, ChangeActor.OPERATOR, SEEDED.plusSeconds(60)),
				new BigStorylineCriteriaChange(initial, first, ChangeActor.OPERATOR, SEEDED.plusSeconds(60)),
				new BigStorylineCriteriaChange(null, initial, ChangeActor.SYSTEM, SEEDED));
	}

	@Test
	void lockingWithoutAValueIsRefused() {
		assertThat(this.repository.current()).isEmpty();
		assertThatIllegalStateException().isThrownBy(this.repository::lockCurrent);
	}

}
