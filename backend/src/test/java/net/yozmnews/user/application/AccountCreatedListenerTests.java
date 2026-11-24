package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountCreated;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계정 생김 리스너 — 커밋 뒤 받은 {@link AccountCreated} 하나에 설정 행 하나와 기본 제외 형식의 제외(이력 원인 ONBOARDING, 시각은 계정을 만든
 * 시각)를 쓴다. 거듭 받아도 한 번이고, 설정 행이 이미 있으면 아무것도 쓰지 않는다. 실제 PostgreSQL 과 기동 때 적재된 기본 체계로 본다.
 * <p>
 * 지키는 규칙: user-R43
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class AccountCreatedListenerTests {

	private static final Instant CREATED = Instant.parse("2026-11-20T06:30:00Z");

	private static final Instant EARLIER = Instant.parse("2026-11-20T06:00:00Z");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	InterestDefaultsWriter defaults;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		AccountEvents.awaitCreatedHandled(this.jdbc);
		UserTables.deleteAll(this.jdbc);
	}

	@Test
	void oneEventWritesTheDefaultsAtTheCreationTime() {
		UserId user = account("grace@example.com");

		publish(new AccountCreated(user, CREATED));

		assertThat(settingsRows(user)).isOne();
		assertThat(this.jdbc.sql("""
				select tag_kind, tag_key, mode from user_tag_preferences where user_id = :user order by tag_key
				""").param("user", user.value()).query().listOfRows()).containsExactly(
				Map.of("tag_kind", "FORMAT", "tag_key", "brief", "mode", "EXCLUDE"),
				Map.of("tag_kind", "FORMAT", "tag_key", "press-release", "mode", "EXCLUDE"));
		assertThat(history(user)).extracting(row -> row.get("cause"), row -> instant(row.get("occurred_at")))
			.containsExactly(tuple("ONBOARDING", CREATED),
					tuple("ONBOARDING", CREATED));
	}

	@Test
	void theSameEventTwiceWritesOnce() {
		UserId user = account("heidi@example.com");

		publish(new AccountCreated(user, CREATED));
		publish(new AccountCreated(user, CREATED));

		assertThat(settingsRows(user)).isOne();
		assertThat(history(user)).hasSize(2);
	}

	@Test
	void anAccountWhoseSettingsRowCameFirstIsLeftAlone() {
		UserId user = account("ivan@example.com");
		this.transaction.executeWithoutResult(status -> this.defaults.createFor(user, EARLIER));

		publish(new AccountCreated(user, CREATED));

		assertThat(settingsRows(user)).isOne();
		assertThat(history(user)).extracting(row -> instant(row.get("occurred_at"))).containsOnly(EARLIER);
	}

	private void publish(AccountCreated created) {
		this.transaction.executeWithoutResult(status -> this.events.publishEvent(created));
		AccountEvents.awaitCreatedHandled(this.jdbc);
	}

	private UserId account(String email) {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
	}

	private long settingsRows(UserId user) {
		return this.jdbc.sql("select count(*) from user_settings where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

	private List<Map<String, Object>> history(UserId user) {
		return this.jdbc.sql("""
				select cause, occurred_at from user_tag_preference_events where user_id = :user order by tag_key
				""").param("user", user.value()).query().listOfRows();
	}

	private static Instant instant(Object value) {
		return ((Timestamp) value).toInstant();
	}

}
