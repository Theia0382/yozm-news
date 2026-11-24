package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.RetiredStoryline;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinesRetired;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserStorylineFixtures.Saved;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
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
 * 사용자 스토리라인 따라가기 — 합쳐진 스토리라인의 추적 씨앗은 남는 스토리라인으로 옮기고(이력 원인 STORYLINE_MERGED), 지운 스토리라인의 추적은
 * 빼고 알린다(이력 원인 STORYLINE_REMOVED · 알림은 풀기의 남은 사건만 담는다). 신호가 관심 창 밖으로 나가고 추적도 없으면 사라지고, 추적 중이면
 * 남는다. 추적 중인 흐름이 갈라지면 조각이 추적을 물려받는다(이력 원인 STORYLINE_SPLIT). 리스너는 커밋한 발행을 받고, 추적하는 사용자를 쪽으로
 * 나눠 모두 따라간다. 실제 PostgreSQL 에서 본다 — 스토리라인과 관계는 행으로 직접 만든다.
 * <p>
 * 지키는 규칙: user-R52 · user-R53 · user-R54 · 사용자 스토리라인(docs/specs/user.md §5)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class UserStorylineFollowupsTests {

	/** 리스너와 followRetired 는 앱 시계의 지금으로 맞춘다 — 그 창 안에 드는 시각 */
	private static final Instant TRACKED = Instant.now().minus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);

	@Autowired
	UserStorylineFollowups followups;

	@Autowired
	StorylineSubscriptions subscriptions;

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
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void aMergedStorylinesTrackedSeedMovesToTheSurvivor() {
		UserId user = user("alice@example.com");
		long survivor = storyline();
		long merged = storyline();
		track(user, merged);
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		int followed = this.followups.followRetired(retired(merged, null));

		assertThat(followed).isEqualTo(1);
		// 추적 시각은 그대로이고 추적 대상 · 추적 씨앗이 남는 스토리라인이다
		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.subscribedAt()).isEqualTo(TRACKED);
			assertThat(one.members()).containsExactly(survivor);
			assertThat(one.seeds()).containsExactly(survivor);
		});
		assertThat(history(user)).containsExactly(List.of(merged, "ADD", "USER"),
				List.of(merged, "REMOVE", "STORYLINE_MERGED"), List.of(survivor, "ADD", "STORYLINE_MERGED"));
		assertThat(this.notices.countUnread(user)).isZero();
	}

	@Test
	void aRemovedStorylinesTrackingIsDroppedAndNoticedWithTheRemainingEvent() {
		UserId user = user("alice@example.com");
		long kept = storyline();
		long removed = storyline();
		track(user, kept);
		track(user, removed);
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		this.followups.followRetired(retired(removed, 777L));

		assertThat(saved(user)).singleElement().extracting(Saved::seeds).isEqualTo(Set.of(kept));
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
		track(user, dissolved);
		track(user, missed);
		this.jdbc.sql("delete from storylines where id in (:ids)").param("ids", List.of(dissolved, missed)).update();

		// 이벤트는 풀린 스토리라인 하나만 싣는다 — 놓친 사라짐(missed)은 남은 사건을 모른다
		int followed = this.followups.followRetired(retired(dissolved, 777L));

		assertThat(followed).isEqualTo(1);
		assertThat(saved(user)).isEmpty();
		assertThat(history(user)).endsWith(List.of(dissolved, "REMOVE", "STORYLINE_REMOVED"),
				List.of(missed, "REMOVE", "STORYLINE_REMOVED"));
		// 따라가기 한 번에 알림 하나 — 항목은 스토리라인 ID 순이고, 이벤트가 싣지 않은 것은 사건이 없다
		assertThat(this.notices.page(user, null, 10)).singleElement()
			.extracting(Notice::content)
			.isEqualTo(new StorylineSubscriptionsRemovedContent(
					List.of(new RemovedSubscription(dissolved, 777L), new RemovedSubscription(missed, null))));
	}

	@Test
	void aTrackedSeedRemovedWhileTheFlowRemainsMovesTheTrackingWithoutANotice() {
		UserId user = user("alice@example.com");
		long top = storyline();
		long child = storyline();
		UserStorylineFixtures.contains(this.jdbc, top, child);
		track(user, child);
		this.subscriptions.view(user, TRACKED.plusSeconds(60));
		// 누른 하위 스토리라인만 지워졌다 — 흐름(꼭대기)에는 반응이 남아 있다
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, top), "LIKE",
				TRACKED.plusSeconds(120));
		this.jdbc.sql("delete from storylines where id = :id").param("id", child).update();

		this.followups.followRetired(retired(child, null));

		// 추적은 흐름에 남고 추적 씨앗이 신호를 받은 꼭대기로 넘어간다 — 사라진 구독 알림은 없다
		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.subscribedAt()).isEqualTo(TRACKED);
			assertThat(one.seeds()).containsExactly(top);
		});
		assertThat(history(user)).endsWith(List.of(child, "REMOVE", "STORYLINE_REMOVED"),
				List.of(top, "ADD", "STORYLINE_SPLIT"));
		assertThat(this.notices.countUnread(user)).isZero();
	}

	@Test
	void anUnrelatedUserIsUntouchedAndARepeatChangesNothing() {
		UserId alice = user("alice@example.com");
		UserId bob = user("bob@example.com");
		long survivor = storyline();
		long merged = storyline();
		long other = storyline();
		track(alice, merged);
		track(bob, other);
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		this.followups.followRetired(retired(merged, null));
		int again = this.followups.followRetired(retired(merged, null));

		// 대조 — 추적하지 않은 사용자는 그대로이고, 다시 처리해도 바뀌는 것 · 이력이 없다
		assertThat(saved(bob)).singleElement().extracting(Saved::seeds).isEqualTo(Set.of(other));
		assertThat(history(bob)).hasSize(1);
		assertThat(again).isZero();
		assertThat(history(alice)).hasSize(3);
	}

	@Test
	void usersBeyondOneBatchAreAllFollowed() {
		long survivor = storyline();
		long merged = storyline();
		for (int i = 0; i < 101; i++) {
			track(user("many" + i + "@example.com"), merged);
		}
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		int followed = this.followups.followRetired(retired(merged, null));

		assertThat(followed).isEqualTo(101);
		assertThat(this.jdbc.sql("select count(*) from user_storyline_tracked_seeds where storyline_id = :id")
			.param("id", survivor)
			.query(Long.class)
			.single()).isEqualTo(101);
	}

	@Test
	void theListenerFollowsACommittedNotification() {
		UserId user = user("alice@example.com");
		long survivor = storyline();
		long merged = storyline();
		track(user, merged);
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(retired(merged, null)));

		awaitSeed(user, survivor);
		assertThat(saved(user)).singleElement().extracting(Saved::seeds).isEqualTo(Set.of(survivor));
	}

	@Test
	void aReactedFlowIsStoredAndVanishesWhenItsSignalsLeaveTheWindow() {
		UserId user = user("alice@example.com");
		long storyline = storyline();
		Instant reacted = Instant.parse("2026-07-01T00:00:00Z");
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, storyline), "DISLIKE",
				reacted);

		// 창 안의 싫어요 하나면 사용자 스토리라인이 있다(추적 중이 아니다)
		this.subscriptions.view(user, reacted.plus(Duration.ofDays(30)));
		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.tracked()).isFalse();
			assertThat(one.members()).containsExactly(storyline);
		});

		// 신호가 창(120일) 밖으로 나가고 추적도 없으면 사라진다
		this.subscriptions.view(user, reacted.plus(Duration.ofDays(121)));
		assertThat(saved(user)).isEmpty();
	}

	@Test
	void anExposureWithoutAReactionMakesNoUserStoryline() {
		UserId user = user("alice@example.com");
		long storyline = storyline();
		// 사건이 그 흐름에 있을 뿐 반응이 없다
		UserStorylineFixtures.eventIn(this.jdbc, storyline);

		this.subscriptions.view(user, TRACKED);

		// 대조 — 신호가 없으면 만들지 않는다
		assertThat(saved(user)).isEmpty();
	}

	@Test
	void aTrackedFlowStaysAfterItsSignalsLeaveTheWindow() {
		UserId user = user("alice@example.com");
		long storyline = storyline();
		Instant tracked = Instant.parse("2026-07-01T00:00:00Z");
		this.subscriptions.subscribe(user, new StorylineId(storyline), tracked);

		this.subscriptions.view(user, tracked.plus(Duration.ofDays(200)));

		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.subscribedAt()).isEqualTo(tracked);
			assertThat(one.seeds()).containsExactly(storyline);
		});
	}

	@Test
	void aSplitPieceInheritsTheTrackingAndStaysAfterItsSignalsLeaveTheWindow() {
		UserId user = user("alice@example.com");
		long earlier = storyline();
		long later = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		Instant tracked = Instant.parse("2026-07-01T00:00:00Z");
		this.subscriptions.subscribe(user, new StorylineId(earlier), tracked);
		// 뒤 흐름에 반응이 있다 — 뒤 흐름도 씨앗이다
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, later), "LIKE",
				tracked.plusSeconds(60));
		this.subscriptions.view(user, tracked.plusSeconds(120));
		assertThat(saved(user)).singleElement().extracting(Saved::members).isEqualTo(Set.of(earlier, later));

		// 이어짐이 사라져 흐름이 둘로 갈린다
		UserStorylineFixtures.unrelate(this.jdbc, earlier, later);
		this.subscriptions.view(user, tracked.plusSeconds(180));

		List<Saved> split = saved(user);
		assertThat(split).hasSize(2).allMatch(Saved::tracked);
		// 저장 개체는 헤드(later) 쪽 조각을 잇고 추적을 물려받는다 — 그 조각의 씨앗 꼭대기가 추적 씨앗이 되고 추적 시각은 원래 것
		assertThat(split.get(0).members()).containsExactly(later);
		assertThat(split.get(0).seeds()).containsExactly(later);
		assertThat(split.get(0).subscribedAt()).isEqualTo(tracked);
		// 추적 씨앗의 조각은 새 개체로 추적 중이다
		assertThat(split.get(1).members()).containsExactly(earlier);
		assertThat(split.get(1).seeds()).containsExactly(earlier);
		assertThat(history(user)).containsExactly(List.of(earlier, "ADD", "USER"), List.of(later, "ADD", "STORYLINE_SPLIT"));

		// 물려받은 조각의 반응이 창 밖으로 나가도 추적 중이라 남는다
		this.subscriptions.view(user, tracked.plus(Duration.ofDays(200)));
		assertThat(saved(user)).hasSize(2).allMatch(Saved::tracked);
	}

	@Test
	void aStoredHeadIsFollowedToItsCurrentTopWhenItsStorylineGetsContained() {
		UserId user = user("alice@example.com");
		// 갈래 둘 — root → left · root → right(left 의 ID 가 작다)
		long root = storyline();
		long left = storyline();
		long right = storyline();
		UserStorylineFixtures.follows(this.jdbc, root, left);
		UserStorylineFixtures.follows(this.jdbc, root, right);
		// 갈라지기 전 root 를 추적하면 갈래마다 사용자 스토리라인 — 먼저 만든 것이 헤드가 작은 left 갈래를 잇는다
		this.subscriptions.subscribe(user, new StorylineId(root), TRACKED);
		List<Saved> before = saved(user);
		assertThat(before).extracting(Saved::members).containsExactly(Set.of(root, left), Set.of(root, right));
		long leftFlow = before.get(0).id();
		long rightFlow = before.get(1).id();

		// 헤드 left 가 ID 가 가장 큰 새 스토리라인의 하위가 된다 — 그 갈래의 꼭대기는 parent 다
		long parent = storyline();
		UserStorylineFixtures.contains(this.jdbc, parent, left);
		this.subscriptions.view(user, TRACKED.plusSeconds(60));

		// 저장한 헤드 left 를 지금 꼭대기 parent 로 풀어 같은 사용자 스토리라인이 그 갈래를 잇는다. 대조 — 풀지 않으면 헤드를 가진
		// 묶음이 없어 추적 씨앗 root 의 묶음 가운데 헤드가 작은 right 갈래로 옮겨 가고, right 갈래의 사용자 스토리라인은 합쳐져 사라진다
		assertThat(saved(user)).extracting(Saved::id, Saved::members, Saved::tracked)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(leftFlow, Set.of(root, parent), true),
					org.assertj.core.groups.Tuple.tuple(rightFlow, Set.of(root, right), true));
	}

	private UserId user(String email) {
		UserId user = UserStorylineFixtures.user(this.jdbc, email);
		this.transaction.executeWithoutResult(status -> this.defaults.createFor(user, TRACKED));
		return user;
	}

	private void track(UserId user, long storyline) {
		UserStorylineFixtures.tracked(this.jdbc, user, storyline, TRACKED);
	}

	private long storyline() {
		return UserStorylineFixtures.storyline(this.jdbc, TRACKED);
	}

	private List<Saved> saved(UserId user) {
		return UserStorylineFixtures.saved(this.jdbc, user);
	}

	private List<List<Object>> history(UserId user) {
		return UserStorylineFixtures.history(this.jdbc, user);
	}

	private static StorylinesRetired retired(long storyline, Long remainingEvent) {
		return new StorylinesRetired(List.of(new RetiredStoryline(new StorylineId(storyline),
				(remainingEvent == null) ? null : new EventId(remainingEvent))));
	}

	private void awaitSeed(UserId user, long storyline) {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < deadline) {
			if (saved(user).stream().anyMatch(one -> one.seeds().contains(storyline))) {
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
		throw new AssertionError("리스너가 추적 씨앗을 옮기지 않았다: " + storyline);
	}

}
