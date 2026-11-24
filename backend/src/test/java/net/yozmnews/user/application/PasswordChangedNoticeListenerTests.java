package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.PasswordChangeMethod;
import net.yozmnews.user.PasswordChanged;
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
 * 비밀번호 알림 리스너 — 커밋 뒤 받은 {@link PasswordChanged} 하나에 그 사용자의 비밀번호 알림 하나를 남긴다. 같은 변경을 거듭 받아도 하나만
 * 남고, 바꾼 시각이 다른 변경은 따로 남는다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R74
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class PasswordChangedNoticeListenerTests {

	private static final Instant CHANGED = Instant.parse("2026-11-20T02:00:00Z");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		AccountEvents.awaitPasswordChangeHandled(this.jdbc);
		UserTables.deleteAll(this.jdbc);
	}

	@Test
	void theSameChangeTwiceLeavesOneNotice() {
		UserId user = account("paula@example.com");

		publish(new PasswordChanged(user, PasswordChangeMethod.CHANGE, CHANGED));
		publish(new PasswordChanged(user, PasswordChangeMethod.CHANGE, CHANGED));

		assertThat(notices(user)).containsExactly("CHANGE@" + CHANGED);
	}

	@Test
	void changesAtDifferentTimesLeaveANoticeEach() {
		UserId user = account("quinn@example.com");

		publish(new PasswordChanged(user, PasswordChangeMethod.CHANGE, CHANGED));
		publish(new PasswordChanged(user, PasswordChangeMethod.RECOVERY, CHANGED.plusSeconds(60)));

		assertThat(notices(user)).containsExactly("CHANGE@" + CHANGED, "RECOVERY@" + CHANGED.plusSeconds(60));
	}

	private void publish(PasswordChanged changed) {
		this.transaction.executeWithoutResult(status -> this.events.publishEvent(changed));
		AccountEvents.awaitPasswordChangeHandled(this.jdbc);
	}

	private UserId account(String email) {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
	}

	private List<String> notices(UserId user) {
		return this.jdbc.sql("""
				select (content ->> 'method') || '@' || (content ->> 'changedAt') from user_notices
				where user_id = :user and kind = 'PASSWORD_CHANGED' order by id
				""").param("user", user.value()).query(String.class).list();
	}

}
