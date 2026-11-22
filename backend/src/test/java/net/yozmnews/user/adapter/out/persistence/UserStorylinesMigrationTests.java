package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import javax.sql.DataSource;

import net.yozmnews.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독 행이 사용자 스토리라인을 가리키게 하는 마이그레이션(V76) — 실제 PostgreSQL 의 별도 스키마에 V75 까지만 올려 옛 구독 행을 넣고, V76 까지
 * 올린 뒤 구독마다 사용자 스토리라인 한 행 · 추적 대상 한 행 · 추적 씨앗 한 행이 생기고 구독 시각이 그대로인지 본다. 앱이 쓰는 스키마와 섞이지
 * 않게 스키마를 따로 두고 시험 뒤 지운다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserStorylinesMigrationTests {

	private static final String SCHEMA = "v75_storyline_subscriptions";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void eachOldSubscriptionBecomesATrackedUserStorylineKeepingItsTime() {
		flyway("75").migrate();
		long alice = user("alice@example.com");
		long bob = user("bob@example.com");
		subscribe(alice, 11, "2026-11-01T00:00:00Z");
		subscribe(alice, 12, "2026-11-02T00:00:00Z");
		subscribe(bob, 11, "2026-11-03T00:00:00Z");

		flyway("76").migrate();

		// 구독마다 사용자 스토리라인 하나 — 추적 대상 · 추적 씨앗이 그 스토리라인이고 구독 시각은 그대로다
		assertThat(rows()).containsExactly(
				List.of(alice, 11L, 11L, OffsetDateTime.parse("2026-11-01T00:00:00Z").toInstant()),
				List.of(alice, 12L, 12L, OffsetDateTime.parse("2026-11-02T00:00:00Z").toInstant()),
				List.of(bob, 11L, 11L, OffsetDateTime.parse("2026-11-03T00:00:00Z").toInstant()));
		assertThat(count("user_storylines")).isEqualTo(3);
		assertThat(count("user_storyline_subscriptions")).isEqualTo(3);
	}

	@Test
	void anEmptySubscriptionTableMigratesToNothing() {
		// 대조 — 옛 구독이 없으면 사용자 스토리라인도 없다
		flyway("76").migrate();

		assertThat(count("user_storylines")).isZero();
		assertThat(count("user_storyline_subscriptions")).isZero();
	}

	/** [사용자, 추적 대상, 추적 씨앗, 구독 시각] — 사용자 · 스토리라인 순 */
	private List<List<Object>> rows() {
		return this.jdbc.sql("select u.user_id, m.storyline_id as member, t.storyline_id as seed, s.created_at from "
				+ SCHEMA + ".user_storylines u join " + SCHEMA + ".user_storyline_members m on m.user_storyline_id = u.id join "
				+ SCHEMA + ".user_storyline_tracked_seeds t on t.user_storyline_id = u.id join " + SCHEMA
				+ ".user_storyline_subscriptions s on s.user_storyline_id = u.id order by u.user_id, m.storyline_id")
			.query((rs, n) -> List.<Object>of(rs.getLong("user_id"), rs.getLong("member"), rs.getLong("seed"),
					rs.getObject("created_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	private long user(String email) {
		return this.jdbc.sql("insert into " + SCHEMA + ".users (email, display_name, role, status, created_at, version) "
				+ "values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id")
			.param("email", email)
			.query(Long.class)
			.single();
	}

	private void subscribe(long user, long storyline, String at) {
		this.jdbc.sql("insert into " + SCHEMA + ".user_storyline_subscriptions (user_id, storyline_id, created_at) "
				+ "values (:user, :storyline, cast(:at as timestamptz))")
			.param("user", user)
			.param("storyline", storyline)
			.param("at", at)
			.update();
	}

	private long count(String table) {
		return this.jdbc.sql("select count(*) from " + SCHEMA + "." + table).query(Long.class).single();
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true).target(target).load();
	}

}
