package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.FeedStatusEntry;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.SeenNotice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 읽은 알림 정리 — 지금에서 90일 전보다 일찍 읽은 알림만 지운다. 정확히 90일 · 89일 전에 읽은 알림과 안 읽은 알림은 남는다.
 * <p>
 * 지키는 규칙: user-R26
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class NoticeCleanupTests {

	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

	private static final Instant CREATED = Instant.parse("2025-11-13T00:00:00Z");

	private static final FeedStatusContent CONTENT = new FeedStatusContent(List.of(new FeedStatusEntry(new FeedId(11),
			null, null, FeedStatus.ACTIVE, FeedStatus.DEGRADED, CREATED, CREATED, 1)));

	@Autowired
	NoticeRepository notices;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void forgetsOnlyNoticesReadMoreThanNinetyDaysAgo() {
		long overdue = readAt("overdue@example.com", "2026-08-14T23:59:59Z");
		long exactly = readAt("exactly@example.com", "2026-08-15T00:00:00Z");
		long recent = readAt("recent@example.com", "2026-08-16T00:00:00Z");
		long unread = this.notices.create(account("unread@example.com"), CONTENT, CREATED);

		int forgotten = new NoticeCleanup(this.notices, () -> NOW).run();

		assertThat(forgotten).isOne();
		assertThat(this.jdbc.sql("select id from user_notices order by id").query(Long.class).list())
			.containsExactly(exactly, recent, unread)
			.doesNotContain(overdue);
	}

	private long readAt(String email, String readAt) {
		UserId user = account(email);
		long id = this.notices.create(user, CONTENT, CREATED);
		this.notices.markRead(user, List.of(new SeenNotice(id, 1)), Instant.parse(readAt));
		return id;
	}

	private UserId account(String email) {
		return this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName(email), AccountRole.ADMIN,
				CREATED)).id();
	}

}
