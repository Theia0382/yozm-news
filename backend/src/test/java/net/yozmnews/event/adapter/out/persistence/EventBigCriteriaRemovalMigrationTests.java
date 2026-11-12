package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * V29 — {@code event_big_criteria} 두 테이블이 없고, 같은 V18 이 만든 기간 조회 인덱스는 남아 있다. 큰 판정 기준은 스토리라인의 테이블에 있다.
 * <p>
 * 지키는 규칙: event-R20 · storyline-R20
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventBigCriteriaRemovalMigrationTests {

	@Autowired
	JdbcClient jdbc;

	@Test
	void theEventCriteriaTablesAreGoneAndThePeriodIndexStays() {
		assertThat(tables("event_big_criteria%")).isEmpty();
		// 대조 — 같은 질의가 있는 테이블은 찾는다
		assertThat(tables("storyline_big_criteria%")).containsExactly("storyline_big_criteria",
				"storyline_big_criteria_changes");
		assertThat(this.jdbc.sql("""
				select indexname from pg_indexes where schemaname = 'public' and tablename = 'event_articles'
				and indexname = 'event_articles_published_idx'
				""").query(String.class).list()).containsExactly("event_articles_published_idx");
	}

	private List<String> tables(String pattern) {
		return this.jdbc.sql("""
				select table_name from information_schema.tables
				where table_schema = 'public' and table_name like :pattern order by table_name
				""").param("pattern", pattern).query(String.class).list();
	}

}
