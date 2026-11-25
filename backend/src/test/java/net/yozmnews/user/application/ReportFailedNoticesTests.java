package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.GenerationFailureCause;
import net.yozmnews.user.ReportFailed;
import net.yozmnews.user.ReportFailedKind;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeContent;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.ReportFailedContent;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 실패 알림 — 실패 하나에 그 사용자의 알림 하나, 같은 실패를 거듭 받아도 하나, 다시 만든 리포트가 또 실패하면(실패 시각이 다르다) 하나
 * 더, 계정이 없으면 남기지 않는다. 트랜잭션에서 낸 이벤트가 리스너를 지나 알림함 응답에 이른다. 실제 PostgreSQL.
 * <p>
 * 지키는 규칙: user-R21 · user-R27 과 리포트 실패 알림(다이제스트 실패 알림 user-R62 와 같은 꼴)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReportFailedNoticesTests {

	private static final Instant FAILED_AT = Instant.parse("2026-11-23T22:05:00.125Z");

	private static final LocalDate FIRST = LocalDate.parse("2026-11-16");

	private static final LocalDate LAST = LocalDate.parse("2026-11-22");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	ReportFailedNotices reportNotices;

	@Autowired
	NoticeRepository notices;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private SignedInUsers users;

	@BeforeEach
	void users() {
		this.users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
	}

	@Test
	void oneFailureLeavesOneNoticeForThatUserOnly() throws Exception {
		UserId alice = this.users.signIn("report-notice-alice@example.com").id();
		UserId bob = this.users.signIn("report-notice-bob@example.com").id();

		this.reportNotices.record(failed(alice, 901, GenerationFailureCause.KEY, FAILED_AT));

		assertThat(contents(alice)).containsExactly(
				new ReportFailedContent(901, ReportFailedKind.WEEK, FIRST, LAST, GenerationFailureCause.KEY, FAILED_AT));
		assertThat(this.notices.page(alice, null, 50)).extracting(Notice::kind, Notice::revision, Notice::read)
			.containsExactly(Tuple.tuple(NoticeKind.REPORT_FAILED, 1, false));
		assertThat(contents(bob)).isEmpty();
	}

	@Test
	void theSameFailureTwiceLeavesOneNoticeAndAFailureAfterRegeneratingLeavesAnother() throws Exception {
		UserId alice = this.users.signIn("report-notice-twice@example.com").id();
		ReportFailed failed = failed(alice, 902, GenerationFailureCause.TEMPORARY, FAILED_AT);

		this.reportNotices.record(failed);
		this.reportNotices.record(failed);

		assertThat(contents(alice)).hasSize(1);

		// 같은 리포트를 다시 만들었는데 또 실패했다 — 실패 시각이 달라 다른 알림이다
		this.reportNotices.record(failed(alice, 902, GenerationFailureCause.OUTPUT, FAILED_AT.plusSeconds(3600)));

		assertThat(contents(alice)).containsExactly(
				new ReportFailedContent(902, ReportFailedKind.WEEK, FIRST, LAST, GenerationFailureCause.OUTPUT,
						FAILED_AT.plusSeconds(3600)),
				new ReportFailedContent(902, ReportFailedKind.WEEK, FIRST, LAST, GenerationFailureCause.TEMPORARY,
						FAILED_AT));
	}

	@Test
	void aFailureOfAMissingAccountLeavesNothingAndDoesNotThrow() throws Exception {
		UserId alice = this.users.signIn("report-notice-gone@example.com").id();
		UserTables.delete(this.jdbc, alice.value());

		this.reportNotices.record(failed(alice, 904, GenerationFailureCause.KEY, FAILED_AT));

		assertThat(this.jdbc.sql("select count(*) from user_notices where user_id = :id")
			.param("id", alice.value())
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void anEventPublishedInATransactionReachesTheNoticeInboxThroughTheListener() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("report-notice-flow@example.com");

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new ReportFailed(user.id(), 905,
				ReportFailedKind.MONTH, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
				GenerationFailureCause.PROVIDER_ACCOUNT, FAILED_AT)));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(contents(user.id())).hasSize(1));
		this.mockMvc.perform(get("/api/notices").with(user))
			.andExpect(jsonPath("$.notices.length()").value(1))
			.andExpect(jsonPath("$.notices[0].kind").value("REPORT_FAILED"))
			.andExpect(jsonPath("$.notices[0].reportFailed.reportId").value(905))
			.andExpect(jsonPath("$.notices[0].reportFailed.kind").value("MONTH"))
			.andExpect(jsonPath("$.notices[0].reportFailed.firstDay").value("2026-10-01"))
			.andExpect(jsonPath("$.notices[0].reportFailed.lastDay").value("2026-10-31"))
			.andExpect(jsonPath("$.notices[0].reportFailed.cause").value("PROVIDER_ACCOUNT"))
			.andExpect(jsonPath("$.notices[0].reportFailed.failedAt").value("2026-11-23T22:05:00.125Z"))
			.andExpect(jsonPath("$.notices[0].digestFailed").isEmpty());

		// 대조 — 트랜잭션이 되돌아가면 이벤트도 알림도 없다
		SignedInUsers.SignedInUser other = this.users.signIn("report-notice-rollback@example.com");
		this.transaction.executeWithoutResult(status -> {
			this.events.publishEvent(failed(other.id(), 906, GenerationFailureCause.KEY, FAILED_AT));
			status.setRollbackOnly();
		});
		await().during(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(contents(other.id())).isEmpty());
	}

	private static ReportFailed failed(UserId user, long reportId, GenerationFailureCause cause, Instant at) {
		return new ReportFailed(user, reportId, ReportFailedKind.WEEK, FIRST, LAST, cause, at);
	}

	private List<NoticeContent> contents(UserId user) {
		return this.notices.page(user, null, 50).stream().map(Notice::content).toList();
	}

}
