package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import net.yozmnews.account.application.ProvisionedAccounts;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.AccountDirectory;
import net.yozmnews.account.domain.account.Onboarding;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기존 계정 채우기 — 설정 행이 없는 계정만 기본값을 얻는다. 형식 제외를 모두 푼 계정(설정 행은 있다)에 되살리지 않고, 다시 돌려도 같다. 묶음
 * 크기를 넘는 계정도 모두 채운다. 방금 만든 계정의 계정 생김 리스너와 겹쳐도 설정 행 · 제외 이력은 한 벌이다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R43 · user-R44
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class InterestBackfillTests {

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	@Autowired
	AccountDirectory accounts;

	@Autowired
	InterestSettingsRepository settings;

	@Autowired
	ApplicationContext context;

	@Autowired
	InterestDefaultsWriter defaults;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private InterestBackfill backfill;

	@BeforeEach
	@AfterEach
	void reset() {
		AccountEvents.awaitCreatedHandled(this.jdbc);
		UserTables.deleteAll(this.jdbc);
		this.backfill = new InterestBackfill(this.accounts, this.settings, this.defaults, this.transaction,
				InstantSource.fixed(NOW));
	}

	@Test
	void onlyAccountsWithoutASettingsRowAreFilled() {
		long before = user("before@example.com");
		long cleared = user("cleared@example.com");
		// 이 계정은 설정 행이 있고 형식 제외를 모두 풀었다
		this.transaction.executeWithoutResult(status -> this.defaults.createFor(new UserId(cleared),
				NOW.minusSeconds(3600)));
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user").param("user", cleared).update();

		assertThat(this.backfill.fill()).isOne();

		assertThat(formats(before)).containsExactly("brief", "press-release");
		assertThat(this.jdbc.sql("select updated_at from user_settings where user_id = :user")
			.param("user", before)
			.query(Timestamp.class)
			.single()
			.toInstant()).isEqualTo(NOW);
		assertThat(formats(cleared)).isEmpty();
		assertThat(this.backfill.fill()).isZero();
	}

	@Test
	void moreAccountsThanOneBatchAreAllFilled() {
		for (int i = 0; i < InterestBackfill.BATCH + 3; i++) {
			user("many" + i + "@example.com");
		}

		assertThat(this.backfill.fill()).isEqualTo(103);
		assertThat(this.jdbc.sql("select count(*) from user_settings").query(Long.class).single()).isEqualTo(103);
	}

	@Test
	void aJustCreatedAccountAndTheBackfillTogetherWriteOneSetOfDefaults() {
		long user = ProvisionedAccounts
			.provision(this.context, Onboarding.FIRST_ADMIN, "kim@example.com", "kim", "initial horse battery", NOW)
			.orElseThrow()
			.value();

		this.backfill.fill();
		AccountEvents.awaitCreatedHandled(this.jdbc);

		assertThat(this.jdbc.sql("select count(*) from user_settings where user_id = :user")
			.param("user", user)
			.query(Long.class)
			.single()).isOne();
		assertThat(this.jdbc.sql("""
				select count(*) from user_tag_preference_events where user_id = :user and cause = 'ONBOARDING'
				""").param("user", user).query(Long.class).single()).isEqualTo(2);
	}

	private long user(String email) {
		return this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single();
	}

	private List<String> formats(long user) {
		return this.jdbc.sql("""
				select tag_key from user_tag_preferences where user_id = :user and tag_kind = 'FORMAT' order by tag_key
				""").param("user", user).query(String.class).list();
	}

}
