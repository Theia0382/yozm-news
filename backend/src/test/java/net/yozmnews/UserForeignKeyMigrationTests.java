package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모듈 사이 FK 를 지우는 마이그레이션(V82) — 실제 PostgreSQL 의 별도 스키마에 V81 까지 올렸을 때와 끝까지 올렸을 때, {@code users} 를
 * 가리키는 FK 를 가진 표를 견준다. 끝까지 올리면 계정 쪽 표 셋만 남고, 관심 · 알림 표는 없는 계정 ID 로도 넣을 수 있되 1 보다 작은 ID 는
 * 검사가 거부한다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤 지운다.
 * <p>
 * 지키는 규칙: ADR-0308(모듈 사이라 FK 가 없다) · docs/specs/user.md §6 · docs/specs/account.md §6
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserForeignKeyMigrationTests {

	private static final String SCHEMA = "v82_user_foreign_keys";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void onlyAccountTablesKeepAForeignKeyToUsers() {
		flyway("81").migrate();
		assertThat(tablesReferencingUsers()).containsExactly("user_excluded_sources", "user_feed_status_receipts",
				"user_invitations", "user_notices", "user_password_credentials", "user_recovery_codes", "user_settings",
				"user_storyline_subscription_events", "user_storyline_subscriptions", "user_storylines",
				"user_tag_preference_events", "user_tag_preferences", "user_topic_suggestion_declines");

		flyway(null).migrate();

		assertThat(tablesReferencingUsers()).containsExactly("user_invitations", "user_password_credentials",
				"user_recovery_codes");
	}

	@Test
	void interestRowsTakeAnyPositiveUserIdAndAccountRowsStillNeedTheAccount() {
		flyway("81").migrate();
		assertThatExceptionOfType(DataIntegrityViolationException.class).as("V81 까지는 FK 가 막는다")
			.isThrownBy(() -> excludedSource(999_999));

		flyway(null).migrate();

		assertThat(excludedSource(999_999)).isOne();
		assertThatExceptionOfType(DataIntegrityViolationException.class).as("1 보다 작은 ID 는 검사가 거부한다")
			.isThrownBy(() -> excludedSource(0));
		// 대조 — 계정 모듈 안의 FK 는 남는다
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.jdbc.sql("insert into " + SCHEMA + ".user_password_credentials (user_id, password_hash, "
					+ "password_changed_at, version) values (999999, 'hash', now(), 0)").update());
	}

	private int excludedSource(long user) {
		return this.jdbc.sql("insert into " + SCHEMA + ".user_excluded_sources (user_id, source_id, created_at) "
				+ "values (:user, 1, now())").param("user", user).update();
	}

	private List<String> tablesReferencingUsers() {
		return this.jdbc.sql("""
				select child.relname from pg_constraint c
				join pg_class child on child.oid = c.conrelid
				join pg_class parent on parent.oid = c.confrelid
				join pg_namespace n on n.oid = parent.relnamespace
				where c.contype = 'f' and parent.relname = 'users' and n.nspname = :schema
				order by child.relname
				""").param("schema", SCHEMA).query(String.class).list();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
