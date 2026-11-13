package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.FeedStatusTransition;
import net.yozmnews.source.FeedStatusesChanged;
import net.yozmnews.source.SourceAdministration;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceRegistrationResult;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 피드 상태 알림 — 실제 피드 이름 조회 · DB 로 회차를 직접 반영하고, 결선 갈래 하나는 이벤트를 발행해 리스너를 거친다. 저장된 내용은 손으로
 * 쓴 JSON 과 STRICT 로 견준다(피드 식별 · 상태 · 시각 · 횟수만 — 비밀 값 · 다른 계정 정보가 없다).
 * <p>
 * 지키는 규칙: user-R22 · user-R23 · user-R24
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class FeedStatusNoticesTests {

	private static final AtomicInteger SOURCES = new AtomicInteger();

	private static final Instant NOW = Instant.parse("2026-11-13T03:00:00Z");

	private static final Instant T1 = Instant.parse("2026-11-13T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-13T01:00:00Z");

	private static final Instant T3 = Instant.parse("2026-11-13T02:00:00Z");

	private static final String PROFILE = """
			source:
			  name: 알림일보%1$d
			  homepage: https://notice%1$d.example
			  language: ko
			termsReviewedAt: 2026-10-01
			feeds:
			  - key: a
			    url: https://notice%1$d.example/a.xml
			    section: 정치
			  - key: b
			    url: https://notice%1$d.example/b.xml
			    section: 경제
			""";

	@Autowired
	SourceAdministration sources;

	@Autowired
	FeedLabels labels;

	@Autowired
	NoticeRecipients recipients;

	@Autowired
	NoticeRepository notices;

	@Autowired
	AccountRepository accounts;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private FeedStatusNotices recording;

	private int source;

	private FeedId a;

	private FeedId b;

	@BeforeEach
	void setUp() {
		this.jdbc.sql("delete from users").update();
		this.recording = new FeedStatusNotices(this.labels, this.recipients, this.notices, () -> NOW);
		this.source = SOURCES.incrementAndGet();
		SourceRegistrationResult registered = this.sources.registerOperatorProfile(PROFILE.formatted(this.source));
		assertThat(registered).isInstanceOf(SourceRegistrationResult.Registered.class);
		SourceId id = ((SourceRegistrationResult.Registered) registered).id();
		this.a = feed(id, "a");
		this.b = feed(id, "b");
	}

	@AfterEach
	void tearDown() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void onlyActiveAdminsReceiveTheChangesOfARun() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		UserId bob = account("bob@example.com", AccountRole.ADMIN);
		UserId carol = account("carol@example.com", AccountRole.USER);
		UserId dave = account("dave@example.com", AccountRole.ADMIN);
		this.jdbc.sql("update users set status = 'SUSPENDED' where id = :id").param("id", dave.value()).update();

		this.recording.record(changed(T1, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));

		assertThat(owners()).containsExactly(alice.value(), bob.value());
		assertThat(notices(carol)).isZero();
		assertThat(notices(dave)).isZero();
		assertThat(row(alice)).isEqualTo("FEED_STATUS 1 2026-11-13T03:00:00Z 2026-11-13T03:00:00Z null");
		JSONAssert.assertEquals("""
				{"feeds": [{"feedId": %d, "sourceName": "알림일보%d", "section": "정치", "firstFrom": "ACTIVE",
				  "lastTo": "DEGRADED", "firstAt": "2026-11-13T00:00:00Z", "lastAt": "2026-11-13T00:00:00Z",
				  "changes": 1}]}
				""".formatted(this.a.value(), this.source), content(alice), JSONCompareMode.STRICT);
		assertThat(receipt(alice)).isEqualTo("2026-11-13T00:00:00Z");
	}

	@Test
	void runsBeforeTheNoticeIsReadGatherInTheSameNotice() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		this.recording.record(changed(T1, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));

		this.recording.record(changed(T2, transition(this.a, FeedStatus.DEGRADED, FeedStatus.ACTIVE),
				transition(this.b, FeedStatus.ACTIVE, FeedStatus.STALE)));

		assertThat(notices(alice)).isOne();
		assertThat(row(alice)).isEqualTo("FEED_STATUS 2 2026-11-13T03:00:00Z 2026-11-13T03:00:00Z null");
		JSONAssert.assertEquals("""
				{"feeds": [
				  {"feedId": %d, "sourceName": "알림일보%d", "section": "정치", "firstFrom": "ACTIVE", "lastTo": "ACTIVE",
				   "firstAt": "2026-11-13T00:00:00Z", "lastAt": "2026-11-13T01:00:00Z", "changes": 2},
				  {"feedId": %d, "sourceName": "알림일보%d", "section": "경제", "firstFrom": "ACTIVE", "lastTo": "STALE",
				   "firstAt": "2026-11-13T01:00:00Z", "lastAt": "2026-11-13T01:00:00Z", "changes": 1}]}
				""".formatted(this.a.value(), this.source, this.b.value(), this.source), content(alice),
				JSONCompareMode.STRICT);
		assertThat(receipt(alice)).isEqualTo("2026-11-13T01:00:00Z");
	}

	@Test
	void aRunProcessedAgainOrArrivingLateChangesNothing() {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		this.recording.record(changed(T2, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));
		String before = content(alice);

		this.recording.record(changed(T2, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));
		this.recording.record(changed(T1, transition(this.b, FeedStatus.ACTIVE, FeedStatus.STALE)));

		assertThat(notices(alice)).isOne();
		assertThat(row(alice)).isEqualTo("FEED_STATUS 1 2026-11-13T03:00:00Z 2026-11-13T03:00:00Z null");
		assertThat(content(alice)).isEqualTo(before);
		assertThat(receipt(alice)).isEqualTo("2026-11-13T01:00:00Z");
	}

	@Test
	void afterReadingOnlyALaterRunStartsANewNotice() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);
		this.recording.record(changed(T2, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));
		long read = this.jdbc.sql("select id from user_notices where user_id = :user")
			.param("user", alice.value())
			.query(Long.class)
			.single();
		this.notices.markRead(alice, List.of(new SeenNotice(read, 1)), NOW);

		this.recording.record(changed(T2, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));
		this.recording.record(changed(T1, transition(this.b, FeedStatus.ACTIVE, FeedStatus.STALE)));
		int afterLateRuns = notices(alice);
		this.recording.record(changed(T3, transition(this.a, FeedStatus.DEGRADED, FeedStatus.ACTIVE)));

		assertThat(afterLateRuns).isOne();
		assertThat(notices(alice)).isEqualTo(2);
		String unread = this.jdbc.sql("select content::text from user_notices where user_id = :user and read_at is null")
			.param("user", alice.value())
			.query(String.class)
			.single();
		JSONAssert.assertEquals("""
				{"feeds": [{"feedId": %d, "sourceName": "알림일보%d", "section": "정치", "firstFrom": "DEGRADED",
				  "lastTo": "ACTIVE", "firstAt": "2026-11-13T02:00:00Z", "lastAt": "2026-11-13T02:00:00Z",
				  "changes": 1}]}
				""".formatted(this.a.value(), this.source), unread, JSONCompareMode.STRICT);
	}

	@Test
	void aFeedThatNoLongerExistsIsKeptWithoutAName() throws Exception {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);

		this.recording.record(changed(T1, transition(new FeedId(999_999), FeedStatus.ACTIVE, FeedStatus.STALE)));

		JSONAssert.assertEquals("""
				{"feeds": [{"feedId": 999999, "sourceName": null, "section": null, "firstFrom": "ACTIVE",
				  "lastTo": "STALE", "firstAt": "2026-11-13T00:00:00Z", "lastAt": "2026-11-13T00:00:00Z",
				  "changes": 1}]}
				""", content(alice), JSONCompareMode.STRICT);
	}

	@Test
	void aRunWithoutAdminsLeavesNothing() {
		account("carol@example.com", AccountRole.USER);

		this.recording.record(changed(T1, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED)));

		assertThat(this.jdbc.sql("select count(*) from user_notices").query(Integer.class).single()).isZero();
		assertThat(this.jdbc.sql("select count(*) from user_feed_status_receipts").query(Integer.class).single())
			.isZero();
	}

	@Test
	void aPublishedRunReachesAdminsThroughTheListener() {
		UserId alice = account("alice@example.com", AccountRole.ADMIN);

		this.transaction.executeWithoutResult(status -> this.events
			.publishEvent(changed(T1, transition(this.a, FeedStatus.ACTIVE, FeedStatus.DEGRADED))));
		await().atMost(Duration.ofSeconds(10)).until(() -> notices(alice) == 1);

		assertThat(receipt(alice)).isEqualTo("2026-11-13T00:00:00Z");
	}

	private UserId account(String email, AccountRole role) {
		return this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(),
				new DisplayName(email), role, T1)).id();
	}

	private FeedId feed(SourceId source, String key) {
		return new FeedId(this.jdbc.sql("select id from feeds where source_id = :source and feed_key = :key")
			.param("source", source.value())
			.param("key", key)
			.query(Long.class)
			.single());
	}

	private List<Long> owners() {
		return this.jdbc.sql("select user_id from user_notices order by user_id").query(Long.class).list();
	}

	private int notices(UserId user) {
		return this.jdbc.sql("select count(*) from user_notices where user_id = :user")
			.param("user", user.value())
			.query(Integer.class)
			.single();
	}

	/** 사용자의 알림 하나 — "종류 판 만든시각 고친시각 읽은시각" */
	private String row(UserId user) {
		return this.jdbc.sql("""
				select kind, revision,
				    to_char(created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created,
				    to_char(updated_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as updated,
				    to_char(read_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as read
				from user_notices where user_id = :user
				""")
			.param("user", user.value())
			.query((rs, row) -> rs.getString("kind") + " " + rs.getInt("revision") + " " + rs.getString("created") + " "
					+ rs.getString("updated") + " " + rs.getString("read"))
			.single();
	}

	private String content(UserId user) {
		return this.jdbc.sql("select content::text from user_notices where user_id = :user")
			.param("user", user.value())
			.query(String.class)
			.single();
	}

	private String receipt(UserId user) {
		return this.jdbc.sql("""
				select to_char(received_through at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
				from user_feed_status_receipts where user_id = :user
				""").param("user", user.value()).query(String.class).single();
	}

	private static FeedStatusesChanged changed(Instant at, FeedStatusTransition... transitions) {
		return new FeedStatusesChanged(at, List.of(transitions));
	}

	private static FeedStatusTransition transition(FeedId feed, FeedStatus from, FeedStatus to) {
		return new FeedStatusTransition(feed, from, to);
	}

}
