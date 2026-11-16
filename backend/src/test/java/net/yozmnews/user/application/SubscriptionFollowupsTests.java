package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
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
 * 구독 따라가기 — 합쳐진 스토리라인의 구독은 남는 스토리라인으로 옮기고(이력 원인 STORYLINE_MERGED), 지운 스토리라인의 구독은 빼고 알린다(이력
 * 원인 STORYLINE_REMOVED · 알림은 풀기의 남은 사건만 담는다). 리스너는 커밋한 발행을 받고, 구독자를 쪽으로 나눠 모두 따라간다. 실제
 * PostgreSQL 에서 본다 — 스토리라인은 행으로 직접 만든다.
 * <p>
 * 지키는 규칙: user-R52 · user-R53 · user-R54
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class SubscriptionFollowupsTests {

	private static final Instant SUBSCRIBED = Instant.parse("2026-11-10T00:00:00Z");

	@Autowired
	SubscriptionFollowups followups;

	@Autowired
	StorylineSubscriptionRepository subscriptions;

	@Autowired
	InterestDefaultsWriter defaults;

	@Autowired
	NoticeRepository notices;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void aMergedStorylinesSubscriptionMovesToTheSurvivor() {
		UserId user = user("alice@example.com");
		long survivor = storyline();
		long merged = storyline();
		subscribe(user, merged);
		merge(merged, survivor);

		int followed = this.followups.followRetired(retired(merged, null));

		assertThat(followed).isEqualTo(1);
		// 구독 시각은 옮긴 행의 것이다
		assertThat(this.subscriptions.load(user)).containsExactly(Map.entry(survivor, SUBSCRIBED));
		assertThat(history(user)).containsExactly(List.of(merged, "ADD", "USER"), List.of(merged, "REMOVE", "STORYLINE_MERGED"),
				List.of(survivor, "ADD", "STORYLINE_MERGED"));
		assertThat(this.notices.countUnread(user)).isZero();
	}

	@Test
	void aRemovedStorylinesSubscriptionIsDroppedAndNoticedWithTheRemainingEvent() {
		UserId user = user("alice@example.com");
		long kept = storyline();
		long removed = storyline();
		subscribe(user, kept);
		subscribe(user, removed);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		this.followups.followRetired(retired(removed, 777L));

		assertThat(this.subscriptions.load(user)).containsOnlyKeys(kept);
		assertThat(history(user)).endsWith(List.of(removed, "REMOVE", "STORYLINE_REMOVED"));
		List<Notice> page = this.notices.page(user, null, 10);
		assertThat(page).singleElement()
			.extracting(Notice::content)
			.isEqualTo(new StorylineSubscriptionsRemovedContent(List.of(new RemovedSubscription(removed, 777L))));
	}

	@Test
	void aRemovalWithoutARemainingEventIsNoticedWithoutAnEvent() {
		UserId user = user("alice@example.com");
		long dissolved = storyline();
		long missed = storyline();
		subscribe(user, dissolved);
		subscribe(user, missed);
		this.jdbc.sql("delete from storylines where id in (:ids)").param("ids", List.of(dissolved, missed)).update();

		// 이벤트는 풀린 스토리라인 하나만 싣는다 — 놓친 사라짐(missed)은 남은 사건을 모른다
		int followed = this.followups.followRetired(retired(dissolved, 777L));

		assertThat(followed).isEqualTo(1);
		assertThat(this.subscriptions.load(user)).isEmpty();
		assertThat(history(user)).endsWith(List.of(dissolved, "REMOVE", "STORYLINE_REMOVED"),
				List.of(missed, "REMOVE", "STORYLINE_REMOVED"));
		// 따라가기 한 번에 알림 하나 — 항목은 스토리라인 ID 순이고, 이벤트가 싣지 않은 것은 사건이 없다
		assertThat(this.notices.page(user, null, 10)).singleElement()
			.extracting(Notice::content)
			.isEqualTo(new StorylineSubscriptionsRemovedContent(
					List.of(new RemovedSubscription(dissolved, 777L), new RemovedSubscription(missed, null))));
	}

	@Test
	void anUnrelatedSubscriberIsUntouchedAndARepeatChangesNothing() {
		UserId alice = user("alice@example.com");
		UserId bob = user("bob@example.com");
		long survivor = storyline();
		long merged = storyline();
		long other = storyline();
		subscribe(alice, merged);
		subscribe(bob, other);
		merge(merged, survivor);

		this.followups.followRetired(retired(merged, null));
		int again = this.followups.followRetired(retired(merged, null));

		// 대조 — 구독하지 않은 사용자는 그대로이고, 다시 처리해도 바뀌는 것 · 이력이 없다
		assertThat(this.subscriptions.load(bob)).containsOnlyKeys(other);
		assertThat(history(bob)).hasSize(1);
		assertThat(again).isZero();
		assertThat(history(alice)).hasSize(3);
	}

	@Test
	void subscribersBeyondOneBatchAreAllFollowed() {
		long survivor = storyline();
		long merged = storyline();
		for (int i = 0; i < 101; i++) {
			UserId user = user("many" + i + "@example.com");
			subscribe(user, merged);
		}
		merge(merged, survivor);

		int followed = this.followups.followRetired(retired(merged, null));

		assertThat(followed).isEqualTo(101);
		assertThat(this.jdbc.sql("select count(*) from user_storyline_subscriptions where storyline_id = :id")
			.param("id", survivor)
			.query(Long.class)
			.single()).isEqualTo(101);
	}

	@Test
	void theListenerFollowsACommittedNotification() {
		UserId user = user("alice@example.com");
		long survivor = storyline();
		long merged = storyline();
		subscribe(user, merged);
		merge(merged, survivor);

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(retired(merged, null)));
		awaitSubscription(user, survivor);

		assertThat(this.subscriptions.load(user)).containsOnlyKeys(survivor);
	}

	private UserId user(String email) {
		long id = this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single();
		UserId user = new UserId(id);
		this.transaction.executeWithoutResult(status -> this.defaults.createFor(user, SUBSCRIBED));
		return user;
	}

	private void subscribe(UserId user, long storyline) {
		this.transaction.executeWithoutResult(status -> {
			this.subscriptions.add(user, storyline, SUBSCRIBED);
			this.subscriptions.record(user, List.of(new SubscriptionChange(storyline, StorylineSubscriptionAction.ADD)),
					StorylineSubscriptionCause.USER, SUBSCRIBED);
		});
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", SUBSCRIBED.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	private void merge(long merged, long survivor) {
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
	}

	private static StorylinesRetired retired(long storyline, Long remainingEvent) {
		return new StorylinesRetired(List.of(new RetiredStoryline(new StorylineId(storyline),
				(remainingEvent == null) ? null : new EventId(remainingEvent))));
	}

	/** 이력 행 — [스토리라인 ID, 동작, 원인], 행 순 */
	private List<List<Object>> history(UserId user) {
		List<List<Object>> rows = new ArrayList<>();
		this.jdbc.sql("""
				select storyline_id, action, cause from user_storyline_subscription_events
				where user_id = :user order by id
				""").param("user", user.value()).query((rs, n) -> {
			rows.add(List.of(rs.getLong("storyline_id"), rs.getString("action"), rs.getString("cause")));
			return n;
		}).list();
		return rows;
	}

	private void awaitSubscription(UserId user, long storyline) {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < deadline) {
			if (this.subscriptions.load(user).containsKey(storyline)) {
				return;
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		throw new AssertionError("리스너가 구독을 옮기지 않았다: " + storyline);
	}

}
