package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.yozmnews.account.application.PasswordHashing;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestFailed;
import net.yozmnews.user.GenerationFailureCause;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.domain.notice.DigestFailedContent;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeContent;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.NoticeRepository;
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
 * 다이제스트 실패 알림 — 앱 전체를 실제 PostgreSQL 로 띄워, 실패 이벤트 하나가 그 사용자의 알림 하나가 되고 같은 다이제스트를 거듭 받아도 하나임을,
 * 계정이 없으면 예외 없이 남기지 않음을 본다. 트랜잭션에서 낸 이벤트가 리스너를 지나 알림함 응답까지 이어지는 경로를 본다 — 리스너는 커밋 뒤에
 * 비동기로 돌아 알림을 기다린다. 기대값은 손으로 쓴 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DigestFailedNoticesTests {

	private static final Instant FAILED_AT = Instant.parse("2026-11-18T22:03:10.250Z");

	private static final LocalDate DATE = LocalDate.parse("2026-11-19");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	DigestFailedNotices digestNotices;

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
		UserId alice = this.users.signIn("digest-notice-alice@example.com").id();
		UserId bob = this.users.signIn("digest-notice-bob@example.com").id();

		this.digestNotices.record(new DigestFailed(alice, 901, DATE, GenerationFailureCause.KEY, FAILED_AT));

		assertThat(contents(alice)).containsExactly(new DigestFailedContent(901, DATE, GenerationFailureCause.KEY, FAILED_AT));
		assertThat(this.notices.page(alice, null, 50)).extracting(Notice::kind, Notice::revision, Notice::read)
			.containsExactly(Tuple.tuple(NoticeKind.DIGEST_FAILED, 1, false));
		assertThat(contents(bob)).isEmpty();
	}

	@Test
	void theSameDigestReceivedTwiceLeavesOneNoticeAndAnotherDigestLeavesAnother() throws Exception {
		UserId alice = this.users.signIn("digest-notice-twice@example.com").id();
		DigestFailed failed = new DigestFailed(alice, 902, DATE, GenerationFailureCause.TEMPORARY, FAILED_AT);

		this.digestNotices.record(failed);
		this.digestNotices.record(failed);

		assertThat(contents(alice)).hasSize(1);

		// 같은 날짜의 다른 다이제스트(수동 생성)가 실패했다 — 다른 알림이다
		this.digestNotices
			.record(new DigestFailed(alice, 903, DATE, GenerationFailureCause.OUTPUT, FAILED_AT.plusSeconds(600)));

		assertThat(contents(alice)).containsExactly(
				new DigestFailedContent(903, DATE, GenerationFailureCause.OUTPUT, FAILED_AT.plusSeconds(600)),
				new DigestFailedContent(902, DATE, GenerationFailureCause.TEMPORARY, FAILED_AT));
	}

	@Test
	void aFailureOfAMissingAccountLeavesNothingAndDoesNotThrow() throws Exception {
		UserId alice = this.users.signIn("digest-notice-gone@example.com").id();
		UserTables.delete(this.jdbc, alice.value());

		this.digestNotices.record(new DigestFailed(alice, 904, DATE, GenerationFailureCause.KEY, FAILED_AT));

		assertThat(this.jdbc.sql("select count(*) from user_notices where user_id = :id")
			.param("id", alice.value())
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void anEventPublishedInATransactionReachesTheNoticeInboxThroughTheListener() throws Exception {
		SignedInUsers.SignedInUser user = this.users.signIn("digest-notice-flow@example.com");

		this.transaction.executeWithoutResult(status -> this.events
			.publishEvent(new DigestFailed(user.id(), 905, DATE, GenerationFailureCause.PROVIDER_ACCOUNT, FAILED_AT)));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(contents(user.id())).hasSize(1));
		this.mockMvc.perform(get("/api/notices").with(user))
			.andExpect(jsonPath("$.notices.length()").value(1))
			.andExpect(jsonPath("$.notices[0].kind").value("DIGEST_FAILED"))
			.andExpect(jsonPath("$.notices[0].digestFailed.digestDate").value("2026-11-19"))
			.andExpect(jsonPath("$.notices[0].digestFailed.cause").value("PROVIDER_ACCOUNT"))
			.andExpect(jsonPath("$.notices[0].digestFailed.failedAt").value("2026-11-18T22:03:10.250Z"))
			.andExpect(jsonPath("$.notices[0].digestFailed.digestId").doesNotExist())
			.andExpect(jsonPath("$.notices[0].keyInvalidated").isEmpty());

		// 대조 — 트랜잭션이 되돌아가면 이벤트도 알림도 없다
		SignedInUsers.SignedInUser other = this.users.signIn("digest-notice-rollback@example.com");
		this.transaction.executeWithoutResult(status -> {
			this.events.publishEvent(new DigestFailed(other.id(), 906, DATE, GenerationFailureCause.KEY, FAILED_AT));
			status.setRollbackOnly();
		});
		await().during(Duration.ofSeconds(2))
			.atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(contents(other.id())).isEmpty());
	}

	private List<NoticeContent> contents(UserId user) {
		return this.notices.page(user, null, 50).stream().map(Notice::content).toList();
	}

}
