package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.UserTimezones;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용자 시간대 조회 — 설정 행의 시간대, 설정 행이 없으면 계정을 만들 때의 기본값(Asia/Seoul), 계정이 없으면 빈 값. 실제 PostgreSQL 위에서
 * 계정을 만드는 길로 계정과 설정 행을 만든다.
 * <p>
 * 지키는 규칙: user-R60
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class UserTimezonesServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	@Autowired
	UserTimezones timezones;

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@Test
	void theTimezoneOfTheSettingsRow() {
		UserId user = account("timezone-stored@example.com");
		this.jdbc.sql("update user_settings set timezone = 'America/New_York' where user_id = :user")
			.param("user", user.value())
			.update();

		assertThat(this.timezones.timezoneOf(user)).contains(ZoneId.of("America/New_York"));
	}

	@Test
	void anAccountWithoutASettingsRowHasTheTimezoneOfANewAccount() {
		UserId user = account("timezone-missing@example.com");
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user").param("user", user.value()).update();
		this.jdbc.sql("delete from user_settings where user_id = :user").param("user", user.value()).update();

		assertThat(this.timezones.timezoneOf(user)).contains(ZoneId.of("Asia/Seoul"));
		assertThat(this.jdbc.sql("select count(*) from user_settings where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single()).as("쓰지 않는다").isZero();
	}

	@Test
	void noAccountHasNoTimezone() {
		assertThat(this.timezones.timezoneOf(new UserId(Long.MAX_VALUE))).isEmpty();
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return ((ProvisionResult.Created) result).user();
	}

}
