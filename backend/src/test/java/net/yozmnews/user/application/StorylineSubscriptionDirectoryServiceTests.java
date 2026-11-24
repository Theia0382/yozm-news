package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
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
 * 스토리라인 구독의 소비자 조회 — 지금 구독은 남는 스토리라인 ID 로 풀고 사라진 것은 빼며, 기간의 변경은 기록된 ID 와 지금 푼 ID 를 함께 낸다.
 * 저장된 행은 바꾸지 않는다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R55
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineSubscriptionDirectoryServiceTests {

	private static final Instant T1 = Instant.parse("2026-11-10T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-11T00:00:00Z");

	private static final Instant T3 = Instant.parse("2026-11-12T00:00:00Z");

	@Autowired
	StorylineSubscriptionDirectory directory;

	@Autowired
	StorylineSubscriptionRepository repository;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		UserTables.deleteAll(this.jdbc);
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void currentSubscriptionsAreResolvedWithoutRewritingTheRows() {
		UserId user = user();
		long survivor = storyline();
		long merged = storyline();
		long kept = storyline();
		long removed = storyline();
		add(user, merged, T1);
		add(user, survivor, T1);
		add(user, kept, T1);
		add(user, removed, T1);
		merge(merged, survivor);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		List<StorylineId> current = this.directory.subscriptions(user);

		// 합쳐진 행과 남는 행은 하나로, 지운 것은 빠지고, ID 순이다
		assertThat(current).containsExactly(new StorylineId(survivor), new StorylineId(kept));
		// 쓰지 않는다 — 저장된 행은 그대로다
		assertThat(UserStorylineFixtures.saved(this.jdbc, user)).flatExtracting(saved -> saved.seeds())
			.containsExactlyInAnyOrder(merged, survivor, kept, removed);
	}

	@Test
	void changesInAHalfOpenPeriodCarryTheRecordedAndTheCurrentId() {
		UserId user = user();
		long survivor = storyline();
		long merged = storyline();
		long removed = storyline();
		record(user, merged, StorylineSubscriptionAction.ADD, T1);
		record(user, removed, StorylineSubscriptionAction.ADD, T2);
		record(user, merged, StorylineSubscriptionAction.REMOVE, T3);
		merge(merged, survivor);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		List<StorylineSubscriptionChange> changes = this.directory.changes(user, T1, T3);

		// 시작은 들고 끝은 들지 않는다
		assertThat(changes).containsExactly(
				new StorylineSubscriptionChange(new StorylineId(merged), new StorylineId(survivor),
						StorylineSubscriptionAction.ADD, StorylineSubscriptionCause.USER, T1),
				new StorylineSubscriptionChange(new StorylineId(removed), null, StorylineSubscriptionAction.ADD,
						StorylineSubscriptionCause.USER, T2));
		assertThat(this.directory.changes(user, T3, T3.plusSeconds(1)))
			.containsExactly(new StorylineSubscriptionChange(new StorylineId(merged), new StorylineId(survivor),
					StorylineSubscriptionAction.REMOVE, StorylineSubscriptionCause.USER, T3));
		assertThat(this.directory.changes(user, T1, T1)).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.changes(user, T3, T1));
	}

	private UserId user() {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('alice@example.com', 'alice', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single());
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

	private void add(UserId user, long storyline, Instant at) {
		UserStorylineFixtures.tracked(this.jdbc, user, storyline, at);
	}

	private void record(UserId user, long storyline, StorylineSubscriptionAction action, Instant at) {
		this.transaction.executeWithoutResult(status -> this.repository.record(user,
				List.of(new SubscriptionChange(storyline, action)), StorylineSubscriptionCause.USER, at));
	}

}
