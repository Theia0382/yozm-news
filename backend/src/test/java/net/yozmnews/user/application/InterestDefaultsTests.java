package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관심 설정 기본값 — 계정을 만드는 길 넷 모두 계정 생김을 받은 리스너가 커밋 뒤 설정 행(기본값 · 선호 버전 = 활성 버전)과 기본 제외 형식의
 * 제외(이력 원인 ONBOARDING)를 쓴다. 같은 이메일이면 아무것도 쓰지 않고, 설정 행이 있으면 다시 쓰지 않는다. 실제 PostgreSQL 과 기동 때 적재된
 * 기본 체계로 본다.
 * <p>
 * 지키는 규칙: user-R43 · user-R45 · account-R35
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class InterestDefaultsTests {

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	InterestDefaultsWriter defaults;

	@Autowired
	TagSetDirectory tagSets;

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

	@ParameterizedTest
	@EnumSource(Onboarding.class)
	void everyWayOfMakingAnAccountWritesTheDefaults(Onboarding onboarding) {
		UserId user = provision(onboarding, onboarding.name().toLowerCase() + "@example.com");
		long active = this.tagSets.activeTagSet().id().value();

		Map<String, Object> row = this.jdbc.sql("""
				select tag_set_id, timezone, digest_time, interest_text, llm_provider, llm_model, weekly_report,
				  weekly_report_day, monthly_report, updated_at, version
				from user_settings where user_id = :user
				""").param("user", user.value()).query().singleRow();
		assertThat(row).containsEntry("tag_set_id", active)
			.containsEntry("timezone", "Asia/Seoul")
			.containsEntry("digest_time", Time.valueOf(LocalTime.of(7, 0)))
			.containsEntry("interest_text", "")
			.containsEntry("llm_provider", null)
			.containsEntry("llm_model", null)
			.containsEntry("weekly_report", true)
			.containsEntry("weekly_report_day", 1)
			.containsEntry("monthly_report", true)
			.containsEntry("version", 0L);
		assertThat(preferences(user)).containsExactly(Map.of("tag_kind", "FORMAT", "tag_key", "brief", "mode", "EXCLUDE"),
				Map.of("tag_kind", "FORMAT", "tag_key", "press-release", "mode", "EXCLUDE"));
		assertThat(this.jdbc.sql("""
				select cause, tag_set_id, tag_kind, tag_key, action from user_tag_preference_events
				where user_id = :user order by tag_key
				""").param("user", user.value()).query().listOfRows())
			.containsExactly(Map.of("cause", "ONBOARDING", "tag_set_id", active, "tag_kind", "FORMAT", "tag_key", "brief",
					"action", "ADD"),
					Map.of("cause", "ONBOARDING", "tag_set_id", active, "tag_kind", "FORMAT", "tag_key", "press-release",
							"action", "ADD"));
	}

	@Test
	void theSameEmailWritesNoSecondSettingsRow() {
		provision(Onboarding.OPEN_SIGN_UP, "dave@example.com");

		ProvisionResult again = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPERATOR,
				EmailAddress.parse("dave@example.com").orElseThrow(), new DisplayName("데이브"),
				PasswordPolicy.normalize("correct horse battery"), NOW));

		assertThat(again).isInstanceOf(ProvisionResult.EmailTaken.class);
		assertThat(count("user_settings")).isOne();
		assertThat(count("user_tag_preference_events")).isEqualTo(2);
	}

	@Test
	void anAccountWithASettingsRowIsNotWrittenAgain() {
		UserId user = provision(Onboarding.OPEN_SIGN_UP, "erin@example.com");
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user").param("user", user.value()).update();

		Boolean written = this.transaction.execute(status -> this.defaults.createFor(user, NOW.plusSeconds(60)));

		assertThat(written).isFalse();
		assertThat(preferences(user)).isEmpty();
		assertThat(count("user_tag_preference_events")).isEqualTo(2);
	}

	private UserId provision(Onboarding onboarding, String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(onboarding,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		AccountEvents.awaitCreatedHandled(this.jdbc);
		return ((ProvisionResult.Created) result).user();
	}

	private List<Map<String, Object>> preferences(UserId user) {
		return this.jdbc.sql("""
				select tag_kind, tag_key, mode from user_tag_preferences where user_id = :user order by tag_key
				""").param("user", user.value()).query().listOfRows();
	}

	private long count(String table) {
		return this.jdbc.sql("select count(*) from " + table).query(Long.class).single();
	}

}
