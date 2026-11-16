package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.user.application.StorylineSubscriptions.SubscribeOutcome;
import net.yozmnews.user.application.StorylineSubscriptions.SubscribedStoryline;
import net.yozmnews.user.application.StorylineSubscriptions.UnsubscribeOutcome;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스토리라인 구독 · 해제 · 조회 — 받은 ID 를 남는 스토리라인으로 풀어 저장하고, 바뀌지 않으면 이력이 없으며, 상한 100 을 넘지 않는다. 조회 ·
 * 저장 앞에서 먼저 따라가 합쳐진 행을 남는 스토리라인으로 고친다. 현재 구독은 이력을 차례로 재생한 집합과 같다. 끝난 스토리라인의 구독은 남는다.
 * 실제 PostgreSQL 에서 본다 — 스토리라인은 행으로 직접 만든다.
 * <p>
 * 지키는 규칙: user-R52 · user-R53
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineSubscriptionsTests {

	private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

	@Autowired
	StorylineSubscriptions subscriptions;

	@Autowired
	SubscriptionFollowups followups;

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
	void aSubscriptionIsStoredAndRecordedOnceAndASecondTimeChangesNothing() {
		UserId user = user("alice@example.com");
		long storyline = storyline(NOW);

		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline), NOW))
			.isEqualTo(new SubscribeOutcome.Subscribed(new StorylineId(storyline)));
		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline), NOW.plusSeconds(60)))
			.isEqualTo(new SubscribeOutcome.AlreadySubscribed(new StorylineId(storyline)));

		assertThat(this.repository.load(user)).containsExactly(Map.entry(storyline, NOW));
		assertThat(history(user)).containsExactly(List.of(storyline, "ADD", "USER"));
	}

	@Test
	void aMergedIdIsSubscribedAsItsSurvivorAndAnUnknownIdIsRejected() {
		UserId user = user("alice@example.com");
		long survivor = storyline(NOW);
		long merged = storyline(NOW);
		merge(merged, survivor);

		assertThat(this.subscriptions.subscribe(user, new StorylineId(merged), NOW))
			.isEqualTo(new SubscribeOutcome.Subscribed(new StorylineId(survivor)));
		assertThat(this.subscriptions.subscribe(user, new StorylineId(999_999_999L), NOW))
			.isEqualTo(new SubscribeOutcome.UnknownStoryline());
		assertThat(this.repository.load(user)).containsOnlyKeys(survivor);
	}

	@Test
	void theHundredAndFirstSubscriptionIsRejected() {
		UserId user = user("alice@example.com");
		for (int i = 0; i < 99; i++) {
			this.subscriptions.subscribe(user, new StorylineId(storyline(NOW)), NOW);
		}
		// 대조 — 99개에서는 하나 더 구독한다
		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline(NOW)), NOW))
			.isInstanceOf(SubscribeOutcome.Subscribed.class);

		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline(NOW)), NOW))
			.isEqualTo(new SubscribeOutcome.LimitReached(100));
		assertThat(this.repository.load(user)).hasSize(100);
	}

	@Test
	void aMergedIdUnsubscribesItsSurvivor() {
		UserId user = user("alice@example.com");
		long survivor = storyline(NOW);
		long merged = storyline(NOW);
		this.subscriptions.subscribe(user, new StorylineId(survivor), NOW);
		merge(merged, survivor);

		// 합쳐진 옛 ID 로 해제해도 남는 스토리라인의 구독을 푼다
		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(merged), NOW))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(survivor)));
		assertThat(this.repository.load(user)).isEmpty();
		assertThat(history(user)).containsExactly(List.of(survivor, "ADD", "USER"), List.of(survivor, "REMOVE", "USER"));
	}

	@Test
	void anUnsubscribeRemovesTheRowAndAnUnsubscribedOneChangesNothing() {
		UserId user = user("alice@example.com");
		long storyline = storyline(NOW);
		long other = storyline(NOW);
		this.subscriptions.subscribe(user, new StorylineId(storyline), NOW);

		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(storyline), NOW))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(storyline)));
		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(other), NOW))
			.isEqualTo(new UnsubscribeOutcome.NotSubscribed());

		assertThat(this.repository.load(user)).isEmpty();
		assertThat(history(user)).containsExactly(List.of(storyline, "ADD", "USER"), List.of(storyline, "REMOVE", "USER"));
	}

	@Test
	void aViewFollowsFirstAndKeepsAnEndedStoryline() {
		UserId user = user("alice@example.com");
		// 상태는 조회하는 지금 시각으로 정한다 — 진행인 스토리라인은 마지막 기사를 지금으로, 끝난 것은 오래전으로 둔다
		long survivor = storyline(Instant.now());
		long merged = storyline(Instant.now());
		long ended = storyline(Instant.parse("2026-09-01T00:00:00Z"));
		this.subscriptions.subscribe(user, new StorylineId(merged), NOW.minusSeconds(60));
		this.subscriptions.subscribe(user, new StorylineId(ended), NOW);
		// 이벤트 없이 합친다 — 조회 앞의 따라가기만 고친다
		merge(merged, survivor);

		List<SubscribedStoryline> view = this.subscriptions.view(user, NOW).subscriptions();

		// 구독 시각 내림차순 — 끝난 스토리라인의 구독은 그대로다
		assertThat(view).containsExactly(new SubscribedStoryline(new StorylineId(ended), NOW, StorylineState.ENDED),
				new SubscribedStoryline(new StorylineId(survivor), NOW.minusSeconds(60), StorylineState.ACTIVE));
		assertThat(this.subscriptions.view(user, NOW).limit()).isEqualTo(100);
		assertThat(history(user)).contains(List.of(merged, "REMOVE", "STORYLINE_MERGED"),
				List.of(survivor, "ADD", "STORYLINE_MERGED"));
	}

	@Test
	void theCurrentSubscriptionsAreTheReplayOfTheHistory() {
		UserId user = user("alice@example.com");
		long a = storyline(NOW);
		long b = storyline(NOW);
		long c = storyline(NOW);
		this.subscriptions.subscribe(user, new StorylineId(a), NOW);
		this.subscriptions.subscribe(user, new StorylineId(b), NOW);
		this.subscriptions.subscribe(user, new StorylineId(c), NOW);
		this.subscriptions.unsubscribe(user, new StorylineId(a), NOW);
		merge(b, c);
		this.subscriptions.view(user, NOW);

		TreeSet<Long> replayed = new TreeSet<>();
		for (List<Object> row : history(user)) {
			if ("ADD".equals(row.get(1))) {
				replayed.add((Long) row.get(0));
			}
			else {
				replayed.remove((Long) row.get(0));
			}
		}
		assertThat(replayed).containsExactly(c);
		assertThat(this.repository.load(user)).containsOnlyKeys(c);
	}

	@Test
	void followingOutsideATransactionIsRefused() {
		UserId user = user("alice@example.com");

		assertThatExceptionOfType(IllegalTransactionStateException.class)
			.isThrownBy(() -> this.followups.followLocked(user, Map.of(), NOW));
	}

	private UserId user(String email) {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
	}

	private long storyline(Instant lastArticleAt) {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", lastArticleAt.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	private void merge(long merged, long survivor) {
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
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

}
