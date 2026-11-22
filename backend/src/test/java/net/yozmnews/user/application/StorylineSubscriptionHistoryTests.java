package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.application.StorylineSubscriptionHistory.HistoryEntry;
import net.yozmnews.user.application.StorylineSubscriptionHistory.HistoryPage;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import net.yozmnews.user.domain.subscription.SubscriptionChangeCursor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 내 구독 이력 — 원인이 사용자인 행만 최근부터 한 쪽(50건)씩, 같은 시각의 행도 쪽 경계에서 빠지거나 겹치지 않는다. 행의 스토리라인은 지금 남는
 * 쪽으로 풀고 지워졌으면 비운다. 읽기만 한다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R63
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineSubscriptionHistoryTests {

	private static final Instant T1 = Instant.parse("2026-11-10T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-11T00:00:00Z");

	private static final Instant T3 = Instant.parse("2026-11-12T00:00:00Z");

	private static final Instant T4 = Instant.parse("2026-11-13T00:00:00Z");

	@Autowired
	StorylineSubscriptionHistory history;

	@Autowired
	StorylineSubscriptions subscriptions;

	@Autowired
	StorylineSubscriptionRepository repository;

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
	void onlyTheUsersOwnRowsComeNewestFirst() {
		UserId user = user("history-a@example.com");
		UserId other = user("history-b@example.com");
		long first = storyline();
		long moved = storyline();
		long second = storyline();
		record(user, first, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, T1);
		record(user, moved, StorylineSubscriptionAction.REMOVE, StorylineSubscriptionCause.STORYLINE_MERGED, T2);
		record(user, second, StorylineSubscriptionAction.REMOVE, StorylineSubscriptionCause.USER, T3);
		record(other, first, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, T4);

		HistoryPage page = this.history.page(user, null);

		// 합쳐져 옮김 행(T2)과 다른 사용자의 행(T4)은 없다
		assertThat(page.entries()).containsExactly(
				new HistoryEntry(new StorylineId(second), StorylineSubscriptionAction.REMOVE, T3),
				new HistoryEntry(new StorylineId(first), StorylineSubscriptionAction.ADD, T1));
		assertThat(page.next()).isNull();
	}

	@Test
	void pagesOfFiftyContinueAcrossRowsOfTheSameTime() {
		UserId user = user("history-a@example.com");
		long storyline = storyline();
		// 앞의 두 행은 같은 시각이다 — 최근부터 51번째 · 50번째가 되어 쪽 경계에 걸친다
		long sameTimeOlder = recordReturningId(user, storyline, T1);
		long sameTimeNewer = recordReturningId(user, storyline, T1);
		for (int i = 1; i <= 49; i++) {
			record(user, storyline, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER,
					T1.plusSeconds(60L * i));
		}

		HistoryPage first = this.history.page(user, null);

		assertThat(first.entries()).hasSize(50);
		assertThat(first.entries().getFirst().occurredAt()).isEqualTo(T1.plusSeconds(60L * 49));
		assertThat(first.entries().getLast().occurredAt()).isEqualTo(T1);
		assertThat(first.next()).isEqualTo(new SubscriptionChangeCursor(T1, sameTimeNewer));

		HistoryPage second = this.history.page(user, first.next());

		assertThat(second.entries()).containsExactly(
				new HistoryEntry(new StorylineId(storyline), StorylineSubscriptionAction.ADD, T1));
		assertThat(second.next()).isNull();
		assertThat(this.history.page(user, new SubscriptionChangeCursor(T1, sameTimeOlder)).entries())
			.as("마지막 행의 커서 뒤에는 남은 행이 없다")
			.isEmpty();
	}

	@Test
	void exactlyFiftyRowsHaveNoNextPage() {
		UserId user = user("history-a@example.com");
		long storyline = storyline();
		for (int i = 1; i <= 50; i++) {
			record(user, storyline, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER,
					T1.plusSeconds(60L * i));
		}

		HistoryPage page = this.history.page(user, null);

		assertThat(page.entries()).hasSize(50);
		assertThat(page.next()).isNull();
	}

	@Test
	void rowsResolveToTheSurvivingStorylineOrToNothing() {
		UserId user = user("history-a@example.com");
		long survivor = storyline();
		long merged = storyline();
		long removed = storyline();
		long kept = storyline();
		record(user, merged, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, T1);
		record(user, removed, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, T2);
		record(user, kept, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, T3);
		merge(merged, survivor);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		HistoryPage page = this.history.page(user, null);

		assertThat(page.entries()).containsExactly(
				new HistoryEntry(new StorylineId(kept), StorylineSubscriptionAction.ADD, T3),
				new HistoryEntry(null, StorylineSubscriptionAction.ADD, T2),
				new HistoryEntry(new StorylineId(survivor), StorylineSubscriptionAction.ADD, T1));
	}

	@Test
	void readingTheHistoryWritesNothing() {
		UserId user = user("history-a@example.com");
		long survivor = storyline();
		long merged = storyline();
		// 추적 중인 사용자 스토리라인 하나 — 추적 씨앗과 이력(더함 · 사용자)이 merged 다
		UserStorylineFixtures.tracked(this.jdbc, user, merged, T1);
		merge(merged, survivor);
		long events = events(user);

		this.history.page(user, null);

		// 합쳐진 ID 의 구독 행이 있어도 고쳐 쓰지 않는다
		assertThat(UserStorylineFixtures.saved(this.jdbc, user)).singleElement()
			.extracting(saved -> saved.seeds())
			.isEqualTo(Set.of(merged));
		assertThat(events(user)).isEqualTo(events);
		// 대조 — 같은 상태에서 내 구독 조회는 따라가기로 행을 옮기고 이력을 남긴다
		this.subscriptions.view(user, T2);
		assertThat(UserStorylineFixtures.saved(this.jdbc, user)).singleElement()
			.extracting(saved -> saved.seeds())
			.isEqualTo(Set.of(survivor));
		assertThat(events(user)).isGreaterThan(events);
	}

	private UserId user(String email) {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, 'reader', 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", T1.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	private void merge(long merged, long survivor) {
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
	}

	private void record(UserId user, long storyline, StorylineSubscriptionAction action,
			StorylineSubscriptionCause cause, Instant at) {
		this.transaction.executeWithoutResult(status -> this.repository.record(user,
				List.of(new SubscriptionChange(storyline, action)), cause, at));
	}

	/** 사용자 구독 행 하나를 남기고 그 이력 행 ID */
	private long recordReturningId(UserId user, long storyline, Instant at) {
		record(user, storyline, StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, at);
		return this.jdbc.sql("select max(id) from user_storyline_subscription_events where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

	private long events(UserId user) {
		return this.jdbc.sql("select count(*) from user_storyline_subscription_events where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

}
