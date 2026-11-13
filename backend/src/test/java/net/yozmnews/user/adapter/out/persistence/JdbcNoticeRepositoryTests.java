package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.FeedStatusEntry;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeKind;
import net.yozmnews.user.domain.notice.NoticeRecipients;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.SeenNotice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 저장소 — 안 읽은 피드 상태 알림은 사용자마다 하나, 반영 기준 잠금은 같은 사용자를 줄 세우고, 읽음은 받은 판이 그대로인 이
 * 사용자의 알림만 바꾼다. 경합은 두 트랜잭션(스레드 둘 · 래치)으로 재현한다.
 * <p>
 * 지키는 규칙: user-R21 · user-R22 · user-R24 · user-R25
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class JdbcNoticeRepositoryTests {

	private static final Instant T1 = Instant.parse("2026-11-13T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-13T01:00:00Z");

	private static final FeedStatusContent FIRST = new FeedStatusContent(List.of(new FeedStatusEntry(new FeedId(11),
			"알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 1)));

	private static final FeedStatusContent SECOND = new FeedStatusContent(List.of(new FeedStatusEntry(new FeedId(11),
			"알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.ACTIVE, T1, T2, 2)));

	@Autowired
	NoticeRepository notices;

	@Autowired
	NoticeRecipients recipients;

	@Autowired
	AccountRepository accounts;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void storesAndReadsBackTheContentNewestFirst() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		long older = this.notices.create(alice, FIRST, T1);
		this.transaction.executeWithoutResult(status -> this.notices.markRead(alice, List.of(new SeenNotice(older, 1)), T1));
		long newer = this.notices.create(alice, SECOND, T2);

		List<Notice> page = this.notices.page(alice, null, 50);

		assertThat(page).extracting(Notice::id).containsExactly(newer, older);
		assertThat(page.get(0)).isEqualTo(new Notice(newer, NoticeKind.FEED_STATUS, SECOND, 1, T2, T2, null));
		assertThat(page.get(1)).isEqualTo(new Notice(older, NoticeKind.FEED_STATUS, FIRST, 1, T1, T1, T1));
		assertThat(this.notices.page(alice, newer, 50)).extracting(Notice::id).containsExactly(older);
		assertThat(this.notices.page(alice, null, 1)).extracting(Notice::id).containsExactly(newer);
		assertThat(this.notices.countUnread(alice)).isOne();
		JSONAssert.assertEquals("""
				{"feeds": [{"feedId": 11, "sourceName": "알림일보", "section": "정치", "firstFrom": "ACTIVE",
				  "lastTo": "DEGRADED", "firstAt": "2026-11-13T00:00:00Z", "lastAt": "2026-11-13T00:00:00Z",
				  "changes": 1}]}
				""", this.jdbc.sql("select content::text from user_notices where id = :id")
			.param("id", older)
			.query(String.class)
			.single(), JSONCompareMode.STRICT);
	}

	@Test
	void aUserHasAtMostOneUnreadFeedStatusNotice() {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		UserId bob = account("bob@example.com", AccountRole.ADMIN);
		long first = this.notices.create(alice, FIRST, T1);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.notices.create(alice, SECOND, T2));
		this.notices.create(bob, FIRST, T1);
		this.notices.markRead(alice, List.of(new SeenNotice(first, 1)), T2);
		this.notices.create(alice, SECOND, T2);

		assertThat(this.notices.countUnread(alice)).isOne();
		assertThat(this.notices.countUnread(bob)).isOne();
	}

	@Test
	void readingChangesOnlyThisUsersNoticeAtTheRevisionItSaw() {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		UserId bob = account("bob@example.com", AccountRole.ADMIN);
		long notice = this.notices.create(alice, FIRST, T1);
		this.notices.revise(notice, SECOND, T2);

		assertThat(this.notices.markRead(bob, List.of(new SeenNotice(notice, 2)), T2)).isZero();
		assertThat(this.notices.markRead(alice, List.of(new SeenNotice(notice, 1)), T2)).isZero();
		assertThat(this.notices.markRead(alice, List.of(), T2)).isZero();
		assertThat(this.notices.countUnread(alice)).isOne();
		assertThat(this.notices.markRead(alice, List.of(new SeenNotice(notice, 2)), T2)).isOne();
		assertThat(this.notices.markRead(alice, List.of(new SeenNotice(notice, 2)), T2)).isZero();
		assertThat(this.notices.page(alice, null, 50)).singleElement()
			.isEqualTo(new Notice(notice, NoticeKind.FEED_STATUS, SECOND, 2, T1, T2, T2));
	}

	@Test
	void theReceiptStartsEmptyAndKeepsWhatWasSaved() {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);

		Optional<Instant> before = this.transaction.execute(status -> this.notices.lockFeedStatusReceipt(alice));
		this.transaction.executeWithoutResult(status -> {
			this.notices.lockFeedStatusReceipt(alice);
			this.notices.saveFeedStatusReceipt(alice, T2);
		});

		Optional<Instant> after = this.transaction.execute(status -> this.notices.lockFeedStatusReceipt(alice));

		assertThat(before).isEmpty();
		assertThat(after).contains(T2);
	}

	@Test
	void aSecondTransactionWaitsForTheReceiptOfTheSameUser() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		UserId bob = account("bob@example.com", AccountRole.ADMIN);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<Void> holding = CompletableFuture.runAsync(() -> this.transaction.executeWithoutResult(status -> {
			this.notices.lockFeedStatusReceipt(alice);
			locked.countDown();
			await(release);
		}));
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Void> other = CompletableFuture
				.runAsync(() -> this.transaction.executeWithoutResult(status -> this.notices.lockFeedStatusReceipt(bob)));
			other.get(10, TimeUnit.SECONDS);
			CompletableFuture<Void> same = CompletableFuture
				.runAsync(() -> this.transaction.executeWithoutResult(status -> this.notices.lockFeedStatusReceipt(alice)));
			Thread.sleep(500);
			assertThat(same).as("같은 사용자의 반영 기준을 잡은 동안 기다린다").isNotDone();
			release.countDown();
			holding.get(10, TimeUnit.SECONDS);
			same.get(10, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
		}
	}

	@Test
	void aSecondTransactionWaitsForAnExistingReceiptOfTheSameUser() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		this.transaction.executeWithoutResult(status -> {
			this.notices.lockFeedStatusReceipt(alice);
			this.notices.saveFeedStatusReceipt(alice, T1);
		});
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<Void> holding = CompletableFuture.runAsync(() -> this.transaction.executeWithoutResult(status -> {
			this.notices.lockFeedStatusReceipt(alice);
			locked.countDown();
			await(release);
			this.notices.saveFeedStatusReceipt(alice, T2);
		}));
		CompletableFuture<Optional<Instant>> same;
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			same = CompletableFuture
				.supplyAsync(() -> this.transaction.execute(status -> this.notices.lockFeedStatusReceipt(alice)));
			Thread.sleep(500);
			assertThat(same).as("이미 있는 반영 기준 행도 잡은 동안 기다린다").isNotDone();
			release.countDown();
			holding.get(10, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
		}

		assertThat(same.get(10, TimeUnit.SECONDS)).contains(T2);
	}

	@Test
	void aReadThatStartedBeforeARevisionCommitsWaitsAndLeavesTheNoticeUnread() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		long notice = this.notices.create(alice, FIRST, T1);
		CountDownLatch revised = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<Void> revising = CompletableFuture.runAsync(() -> this.transaction.executeWithoutResult(status -> {
			this.notices.lockUnread(alice, NoticeKind.FEED_STATUS).orElseThrow();
			this.notices.revise(notice, SECOND, T2);
			revised.countDown();
			await(release);
		}));
		CompletableFuture<Integer> reading;
		try {
			assertThat(revised.await(10, TimeUnit.SECONDS)).isTrue();
			reading = CompletableFuture.supplyAsync(() -> this.transaction
				.execute(status -> this.notices.markRead(alice, List.of(new SeenNotice(notice, 1)), T2)));
			Thread.sleep(500);
			assertThat(reading).as("고치는 트랜잭션이 알림 행을 잡은 동안 읽음이 기다린다").isNotDone();
			release.countDown();
			revising.get(10, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
		}

		assertThat(reading.get(10, TimeUnit.SECONDS)).isZero();
		assertThat(this.notices.page(alice, null, 50)).singleElement()
			.isEqualTo(new Notice(notice, NoticeKind.FEED_STATUS, SECOND, 2, T1, T2, null));
	}

	@Test
	void aLockThatStartedBeforeAReadCommitsWaitsAndFindsNoUnreadNotice() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		long notice = this.notices.create(alice, FIRST, T1);
		CountDownLatch read = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<Integer> reading = CompletableFuture.supplyAsync(() -> this.transaction.execute(status -> {
			int changed = this.notices.markRead(alice, List.of(new SeenNotice(notice, 1)), T2);
			read.countDown();
			await(release);
			return changed;
		}));
		CompletableFuture<Optional<Notice>> locking;
		try {
			assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
			locking = CompletableFuture.supplyAsync(
					() -> this.transaction.execute(status -> this.notices.lockUnread(alice, NoticeKind.FEED_STATUS)));
			Thread.sleep(500);
			assertThat(locking).as("읽음 트랜잭션이 알림 행을 잡은 동안 잠금이 기다린다").isNotDone();
			release.countDown();
			assertThat(reading.get(10, TimeUnit.SECONDS)).isOne();
		}
		finally {
			release.countDown();
		}

		assertThat(locking.get(10, TimeUnit.SECONDS)).isEmpty();
	}

	@Test
	void recipientsAreActiveAdminsInIdOrder() {
		UserId first = account("first@example.com", AccountRole.ADMIN);
		account("user@example.com", AccountRole.USER);
		UserId suspended = account("suspended@example.com", AccountRole.ADMIN);
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", suspended.value()).update();
		UserId second = account("second@example.com", AccountRole.ADMIN);

		assertThat(this.recipients.activeAdmins()).containsExactly(first, second);
	}

	@Test
	void forgetsOnlyNoticesReadBeforeTheCutoff() {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		UserId bob = account("bob@example.com", AccountRole.ADMIN);
		UserId carol = account("carol@example.com", AccountRole.ADMIN);
		long before = this.notices.create(alice, FIRST, T1);
		long at = this.notices.create(bob, FIRST, T1);
		long unread = this.notices.create(carol, FIRST, T1);
		this.notices.markRead(alice, List.of(new SeenNotice(before, 1)), Instant.parse("2026-11-13T00:59:59Z"));
		this.notices.markRead(bob, List.of(new SeenNotice(at, 1)), T2);

		assertThat(this.notices.forgetReadBefore(T2)).isOne();
		assertThat(this.jdbc.sql("select id from user_notices order by id").query(Long.class).list())
			.containsExactly(at, unread);
	}

	private UserId account(String email, AccountRole role) {
		return this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName(email), role, T1)).id();
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("래치를 기다리다 시간이 지났다");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
