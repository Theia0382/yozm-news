package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.DigestScheduleEntry;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다이제스트 일정 목록 — 쓰는 계정 가운데 설정 행이 있는 사용자의 시간대 · 생성 시각을 ID 순 쪽으로 낸다. 정지 · 탈퇴 계정과 설정 행이 없는
 * 계정은 빠진다. 실제 PostgreSQL.
 * <p>
 * 이 시험의 계정은 마지막에 만들어 ID 가 가장 크다 — 쪽의 시작을 이 시험의 첫 계정 바로 앞으로 두면 다른 시험이 남긴 계정이 섞이지 않는다.
 * 기대값은 손으로 쓴 리터럴이다(계정을 만들 때의 기본값 — `Asia/Seoul` · 07:00).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestScheduleDirectoryTests {

	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	@Autowired
	DigestScheduleDirectory directory;

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		AccountEvents.awaitCreatedHandled(this.jdbc);
		this.jdbc.sql("delete from users where email like 'digest-schedule-%'").update();
	}

	@Test
	void activeAccountsWithSettingsComeInIdOrderAndOthersAreLeftOut() {
		UserId alice = account("digest-schedule-alice@example.com");
		UserId suspended = account("digest-schedule-suspended@example.com");
		UserId withdrawn = account("digest-schedule-withdrawn@example.com");
		UserId unset = account("digest-schedule-unset@example.com");
		UserId bob = account("digest-schedule-bob@example.com");
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended.value()).update();
		this.jdbc.sql("update users set status = 'DELETED' where id = :id").param("id", withdrawn.value()).update();
		this.jdbc.sql("delete from user_settings where user_id = :id").param("id", unset.value()).update();
		this.jdbc.sql("update user_settings set timezone = 'America/New_York', digest_time = '23:30' where user_id = :id")
			.param("id", bob.value())
			.update();

		List<DigestScheduleEntry> page = this.directory.page(alice.value() - 1, DigestScheduleDirectory.MAX_PAGE);

		assertThat(page).containsExactly(new DigestScheduleEntry(alice, ZoneId.of("Asia/Seoul"), LocalTime.of(7, 0)),
				new DigestScheduleEntry(bob, ZoneId.of("America/New_York"), LocalTime.of(23, 30)));
	}

	@Test
	void aPageStopsAtTheLimitAndTheNextPageStartsAfterItsLastUser() {
		UserId first = account("digest-schedule-first@example.com");
		UserId second = account("digest-schedule-second@example.com");
		UserId third = account("digest-schedule-third@example.com");

		List<DigestScheduleEntry> page = this.directory.page(first.value() - 1, 2);
		List<DigestScheduleEntry> next = this.directory.page(second.value(), 2);

		assertThat(page).extracting(DigestScheduleEntry::user).containsExactly(first, second);
		assertThat(next).extracting(DigestScheduleEntry::user).containsExactly(third);
	}

	@Test
	void refusesAPageSizeOutsideOneToTheLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.page(0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.page(0, 501));
		// 대조 — 경계 값은 받는다
		assertThat(this.directory.page(Long.MAX_VALUE - 1, 1)).isEmpty();
		assertThat(this.directory.page(Long.MAX_VALUE - 1, 500)).isEmpty();
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		AccountEvents.awaitCreatedHandled(this.jdbc);
		return ((ProvisionResult.Created) result).user();
	}

}
