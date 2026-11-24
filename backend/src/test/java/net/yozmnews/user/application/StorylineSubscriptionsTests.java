package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.user.UserStorylineFixtures.Saved;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.application.StorylineSubscriptions.SubscribeOutcome;
import net.yozmnews.user.application.StorylineSubscriptions.SubscribedUserStoryline;
import net.yozmnews.user.application.StorylineSubscriptions.UnsubscribeOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * 흐름 추적(스토리라인 구독)의 추적 · 해제 · 조회 · 추적 상태 — 추적은 사용자 스토리라인에 붙고 누른 스토리라인은 추적 씨앗으로 남는다. 받은
 * ID 는 남는 스토리라인으로 풀고, 그 흐름의 어느 스토리라인(하위 · 이어진 뒤 흐름)에서 물어도 같은 추적이다. 해제는 그 흐름의 추적을 끈다.
 * 이력은 추적 씨앗마다 한 행이고 지금 추적 씨앗은 이력을 차례로 재생한 집합과 같다. 추적 중인 사용자 스토리라인은 100 을 넘지 않는다. 실제
 * PostgreSQL 에서 본다 — 스토리라인과 관계는 행으로 직접 만든다.
 * <p>
 * 지키는 규칙: user-R52 · user-R53 · 사용자 스토리라인(docs/specs/user.md §5)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineSubscriptionsTests {

	private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

	@Autowired
	StorylineSubscriptions subscriptions;

	@Autowired
	UserStorylineFollowups followups;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void aTrackingIsStoredAndRecordedOnceAndASecondTimeChangesNothing() {
		UserId user = user();
		long storyline = storyline();

		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline), NOW))
			.isEqualTo(new SubscribeOutcome.Subscribed(new StorylineId(storyline)));
		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline), at(60)))
			.isEqualTo(new SubscribeOutcome.AlreadySubscribed(new StorylineId(storyline)));

		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.subscribedAt()).isEqualTo(NOW);
			assertThat(one.members()).containsExactly(storyline);
			assertThat(one.seeds()).containsExactly(storyline);
		});
		assertThat(history(user)).containsExactly(List.of(storyline, "ADD", "USER"));
	}

	@Test
	void aMergedIdIsTrackedAsItsSurvivorAndAnUnknownIdIsRejected() {
		UserId user = user();
		long survivor = storyline();
		long merged = storyline();
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		assertThat(this.subscriptions.subscribe(user, new StorylineId(merged), NOW))
			.isEqualTo(new SubscribeOutcome.Subscribed(new StorylineId(survivor)));
		assertThat(this.subscriptions.subscribe(user, new StorylineId(999_999_999L), at(60)))
			.isEqualTo(new SubscribeOutcome.UnknownStoryline());
		assertThat(saved(user)).singleElement().extracting(Saved::seeds).isEqualTo(Set.of(survivor));
	}

	@Test
	void pressingAChildTracksTheFlowOfItsTopAndTheListShowsThePressedStoryline() {
		UserId user = user();
		long top = storyline();
		long child = storyline();
		long unrelated = storyline();
		UserStorylineFixtures.contains(this.jdbc, top, child);

		this.subscriptions.subscribe(user, new StorylineId(child), NOW);

		// 추적 대상은 꼭대기, 추적 씨앗은 누른 하위 스토리라인이다
		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.members()).containsExactly(top);
			assertThat(one.seeds()).containsExactly(child);
		});
		// 그 흐름의 어느 스토리라인에서 물어도 추적 중이다. 대조 — 관계없는 스토리라인은 아니다
		assertThat(this.subscriptions.states(user,
				List.of(new StorylineId(top), new StorylineId(child), new StorylineId(unrelated)), at(60)))
			.containsExactly(Map.entry(new StorylineId(top), true), Map.entry(new StorylineId(child), true),
					Map.entry(new StorylineId(unrelated), false));
		// 목록의 줄이 싣는 추적 씨앗은 누른 스토리라인이다 — 꼭대기가 아니다
		assertThat(this.subscriptions.view(user, at(60)).subscriptions()).singleElement()
			.satisfies(row -> assertThat(row.storylines()).containsExactly(new StorylineId(child)));
	}

	@Test
	void trackingAnEarlierFlowTracksItsLaterFlowAndUntrackingFromTheLaterFlowTurnsBothOff() {
		UserId user = user();
		long earlier = storyline();
		long later = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		this.subscriptions.subscribe(user, new StorylineId(earlier), NOW);
		long flow = saved(user).getFirst().id();

		// 앞 흐름을 추적하면 뒤 흐름도 같은 사용자 스토리라인의 추적 대상이다
		assertThat(saved(user)).singleElement().extracting(Saved::members).isEqualTo(Set.of(earlier, later));
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(later)), at(60)))
			.containsExactly(Map.entry(new StorylineId(later), true));

		// 뒤 흐름에서 해제해도 그 흐름을 받게 만든 앞 흐름의 추적을 끈다
		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(later), at(120)))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(later)));
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(earlier), new StorylineId(later)), at(180)))
			.containsExactly(Map.entry(new StorylineId(earlier), false), Map.entry(new StorylineId(later), false));
		assertThat(history(user)).containsExactly(List.of(earlier, "ADD", "USER"), List.of(earlier, "REMOVE", "USER"));

		// 사용자 스토리라인은 남아 있다 — 다시 추적하면 같은 사용자 스토리라인이다
		this.subscriptions.subscribe(user, new StorylineId(later), at(240));
		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.id()).isEqualTo(flow);
			assertThat(one.tracked()).isTrue();
			assertThat(one.seeds()).containsExactly(later);
		});
	}

	@Test
	void trackingTheLaterFlowDoesNotTrackTheEarlierFlow() {
		UserId user = user();
		long earlier = storyline();
		long later = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);

		this.subscriptions.subscribe(user, new StorylineId(later), NOW);

		// 대조 — 이어짐은 앞에서 뒤로만 따른다
		assertThat(saved(user)).singleElement().extracting(Saved::members).isEqualTo(Set.of(later));
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(earlier)), at(60)))
			.containsExactly(Map.entry(new StorylineId(earlier), false));
	}

	@Test
	void anotherStorylineOfATrackedFlowAddsATrackedSeedAndUntrackingRecordsEachSeed() {
		UserId user = user();
		long earlier = storyline();
		long later = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		this.subscriptions.subscribe(user, new StorylineId(earlier), NOW);

		assertThat(this.subscriptions.subscribe(user, new StorylineId(later), at(60)))
			.isEqualTo(new SubscribeOutcome.Subscribed(new StorylineId(later)));

		// 추적 중인 사용자 스토리라인은 그대로 하나이고 추적 씨앗이 둘이다 — 추적 시각은 처음 것
		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.subscribedAt()).isEqualTo(NOW);
			assertThat(one.seeds()).containsExactly(earlier, later);
		});
		assertThat(history(user)).containsExactly(List.of(earlier, "ADD", "USER"), List.of(later, "ADD", "USER"));

		this.subscriptions.unsubscribe(user, new StorylineId(earlier), at(120));

		assertThat(saved(user)).singleElement().satisfies(one -> {
			assertThat(one.tracked()).isFalse();
			assertThat(one.seeds()).isEmpty();
		});
		assertThat(history(user)).endsWith(List.of(earlier, "REMOVE", "USER"), List.of(later, "REMOVE", "USER"));
	}

	@Test
	void theHundredAndFirstTrackedFlowIsRejectedButATrackedFlowTakesAnotherSeed() {
		UserId user = user();
		long earlier = storyline();
		long later = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		this.subscriptions.subscribe(user, new StorylineId(earlier), NOW);
		for (int i = 0; i < 98; i++) {
			UserStorylineFixtures.tracked(this.jdbc, user, storyline(), NOW);
		}
		// 대조 — 99개에서는 하나 더 추적한다
		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline()), at(60)))
			.isInstanceOf(SubscribeOutcome.Subscribed.class);

		assertThat(this.subscriptions.subscribe(user, new StorylineId(storyline()), at(120)))
			.isEqualTo(new SubscribeOutcome.LimitReached(100));
		// 이미 추적 중인 흐름의 다른 스토리라인은 상한과 무관하게 추적 씨앗이 된다
		assertThat(this.subscriptions.subscribe(user, new StorylineId(later), at(180)))
			.isEqualTo(new SubscribeOutcome.Subscribed(new StorylineId(later)));
		assertThat(saved(user).stream().filter(Saved::tracked)).hasSize(100);
	}

	@Test
	void anUntrackTurnsTheTrackingOffAndAnUntrackedFlowChangesNothing() {
		UserId user = user();
		long storyline = storyline();
		long other = storyline();
		this.subscriptions.subscribe(user, new StorylineId(storyline), NOW);

		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(storyline), at(60)))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(storyline)));
		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(other), at(120)))
			.isEqualTo(new UnsubscribeOutcome.NotSubscribed());
		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(999_999_999L), at(180)))
			.isEqualTo(new UnsubscribeOutcome.NotSubscribed());

		assertThat(saved(user)).noneMatch(Saved::tracked);
		assertThat(history(user)).containsExactly(List.of(storyline, "ADD", "USER"), List.of(storyline, "REMOVE", "USER"));
	}

	@Test
	void aMergedIdUntracksTheFlowOfItsSurvivor() {
		UserId user = user();
		long survivor = storyline();
		long merged = storyline();
		this.subscriptions.subscribe(user, new StorylineId(survivor), NOW);
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(merged), at(60)))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(survivor)));
		assertThat(saved(user)).noneMatch(Saved::tracked);
	}

	@Test
	void aViewFollowsFirstAndKeepsAnEndedStoryline() {
		UserId user = user();
		// 상태는 조회하는 지금 시각으로 정한다 — 진행인 스토리라인은 마지막 기사를 지금으로, 끝난 것은 오래전으로 둔다
		long survivor = UserStorylineFixtures.storyline(this.jdbc, Instant.now());
		long merged = UserStorylineFixtures.storyline(this.jdbc, Instant.now());
		long ended = UserStorylineFixtures.storyline(this.jdbc, Instant.parse("2026-09-01T00:00:00Z"));
		this.subscriptions.subscribe(user, new StorylineId(merged), NOW);
		this.subscriptions.subscribe(user, new StorylineId(ended), at(60));
		// 이벤트 없이 합친다 — 조회 앞의 따라가기만 고친다
		UserStorylineFixtures.merge(this.jdbc, merged, survivor);

		List<SubscribedUserStoryline> view = this.subscriptions.view(user, at(120)).subscriptions();

		List<Saved> saved = saved(user);
		// 추적 시각 내림차순 — 끝난 스토리라인의 추적은 그대로이고, 합쳐진 추적 씨앗은 남는 스토리라인으로 옮겼다
		assertThat(view).containsExactly(
				new SubscribedUserStoryline(new UserStorylineId(saved.get(1).id()), new StorylineId(ended), at(60),
						List.of(new StorylineId(ended)), StorylineState.ENDED, null),
				new SubscribedUserStoryline(new UserStorylineId(saved.get(0).id()), new StorylineId(survivor), NOW,
						List.of(new StorylineId(survivor)), StorylineState.ACTIVE, null));
		assertThat(this.subscriptions.view(user, at(180)).limit()).isEqualTo(100);
		assertThat(history(user)).contains(List.of(merged, "REMOVE", "STORYLINE_MERGED"),
				List.of(survivor, "ADD", "STORYLINE_MERGED"));
	}

	@Test
	void aFlowWithTwoTrackedSeedsIsOneRowWithTheLiveliestStateAndTheSummaryTitle() {
		UserId user = user();
		// 앞 흐름은 오래전에 끝났고 뒤 흐름은 지금 이어진다 — 상태는 조회하는 지금 시각으로 정한다
		long earlier = UserStorylineFixtures.storyline(this.jdbc, Instant.parse("2026-09-01T00:00:00Z"));
		long later = UserStorylineFixtures.storyline(this.jdbc, Instant.now());
		long alone = UserStorylineFixtures.storyline(this.jdbc, Instant.parse("2026-09-01T00:00:00Z"));
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		this.subscriptions.subscribe(user, new StorylineId(later), NOW);
		this.subscriptions.subscribe(user, new StorylineId(earlier), at(60));
		this.subscriptions.subscribe(user, new StorylineId(alone), at(120));
		Saved flow = saved(user).stream().filter(one -> one.seeds().size() == 2).findFirst().orElseThrow();
		Saved single = saved(user).stream().filter(one -> one.seeds().size() == 1).findFirst().orElseThrow();
		summarize(flow, "의대 정원 갈등");

		List<SubscribedUserStoryline> view = this.subscriptions.view(user, at(180)).subscriptions();

		// 씨앗 둘인 흐름이 한 줄이다 — 씨앗은 ID 순, 상태는 둘 가운데 이어지는 쪽, 제목은 정리의 제목, 추적 시각은 처음 것.
		// 대조 — 씨앗 하나인 흐름은 그 씨앗의 상태 그대로이고 정리가 없어 제목이 없다
		assertThat(view).containsExactly(
				new SubscribedUserStoryline(new UserStorylineId(single.id()), new StorylineId(alone), at(120),
						List.of(new StorylineId(alone)), StorylineState.ENDED, null),
				new SubscribedUserStoryline(new UserStorylineId(flow.id()), new StorylineId(later), NOW,
						List.of(new StorylineId(earlier), new StorylineId(later)), StorylineState.ACTIVE,
						"의대 정원 갈등"));
	}

	@Test
	void trackingOneBranchLeavesTheSiblingBranchUntracked() {
		UserId user = user();
		Fork fork = fork(user);

		this.subscriptions.subscribe(user, new StorylineId(fork.left()), at(60));

		// 갈라지기 전 꼭대기는 추적 중인 갈래에 들어 추적 중으로 보이고, 형제 갈래(49 → 423)는 추적 중이 아니다
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(fork.left()), new StorylineId(fork.root()),
				new StorylineId(fork.middle()), new StorylineId(fork.right())), at(120)))
			.containsExactly(Map.entry(new StorylineId(fork.left()), true), Map.entry(new StorylineId(fork.root()), true),
					Map.entry(new StorylineId(fork.middle()), false), Map.entry(new StorylineId(fork.right()), false));
		assertThat(this.subscriptions.view(user, at(120)).subscriptions()).extracting(SubscribedUserStoryline::head)
			.containsExactly(new StorylineId(fork.left()));
		// 저장하는 맞추기가 헤드마다 사용자 스토리라인 하나를 두고 헤드 열을 쓴다
		assertThat(this.jdbc.sql("select head from user_storylines where user_id = :user order by head")
			.param("user", user.value())
			.query(Long.class)
			.list()).containsExactly(fork.left(), fork.right());

		// 형제 갈래(middle)는 추적 중이 아니라 거기서 해제할 것이 없고, 추적한 갈래는 그대로다
		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(fork.middle()), at(150)))
			.isEqualTo(new UnsubscribeOutcome.NotSubscribed());
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(fork.left())), at(180)))
			.containsExactly(Map.entry(new StorylineId(fork.left()), true));
	}

	@Test
	void theTopBeforeTheForkIsTrackedWhenAnyOfItsBranchesIsTracked() {
		UserId user = user();
		Fork fork = fork(user);

		// 뒤에 만든 갈래(헤드 right)만 추적한다 — root 를 가진 사용자 스토리라인 가운데 앞의 것(헤드 left)은 추적 중이 아니다
		this.subscriptions.subscribe(user, new StorylineId(fork.right()), at(60));

		assertThat(this.subscriptions.states(user,
				List.of(new StorylineId(fork.root()), new StorylineId(fork.left()), new StorylineId(fork.right())),
				at(120)))
			.containsExactly(Map.entry(new StorylineId(fork.root()), true), Map.entry(new StorylineId(fork.left()), false),
					Map.entry(new StorylineId(fork.right()), true));
	}

	@Test
	void untrackingOneBranchKeepsTheOtherBranchTracked() {
		UserId user = user();
		Fork fork = fork(user);
		this.subscriptions.subscribe(user, new StorylineId(fork.left()), at(60));
		this.subscriptions.subscribe(user, new StorylineId(fork.right()), at(90));

		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(fork.middle()), at(120)))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(fork.middle())));

		assertThat(this.subscriptions.states(user,
				List.of(new StorylineId(fork.left()), new StorylineId(fork.right()), new StorylineId(fork.root())), at(180)))
			.containsExactly(Map.entry(new StorylineId(fork.left()), true), Map.entry(new StorylineId(fork.right()), false),
					Map.entry(new StorylineId(fork.root()), true));
		assertThat(this.subscriptions.view(user, at(180)).subscriptions()).extracting(SubscribedUserStoryline::head)
			.containsExactly(new StorylineId(fork.left()));
	}

	@Test
	void untrackingOneBranchAfterTrackingTheTopBeforeTheForkKeepsTheOtherBranchesOnly() {
		UserId user = user();
		Fork fork = fork(user);
		// 갈라지기 전 root 를 눌러 두 갈래(헤드 left · right) 모두 씨앗 root 로 추적 중
		this.subscriptions.subscribe(user, new StorylineId(fork.root()), at(60));

		assertThat(this.subscriptions.unsubscribe(user, new StorylineId(fork.left()), at(120)))
			.isEqualTo(new UnsubscribeOutcome.Unsubscribed(new StorylineId(fork.left())));

		// 다음 맞추기 뒤에도 left 갈래만 꺼져 있다 — 남은 갈래의 씨앗이 제 헤드라 root 를 거쳐 left 로 돌아가지 않는다
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(fork.left()), new StorylineId(fork.right()),
				new StorylineId(fork.middle()), new StorylineId(fork.root())), at(180)))
			.containsExactly(Map.entry(new StorylineId(fork.left()), false), Map.entry(new StorylineId(fork.right()), true),
					Map.entry(new StorylineId(fork.middle()), true), Map.entry(new StorylineId(fork.root()), true));
		assertThat(this.subscriptions.view(user, at(180)).subscriptions()).singleElement().satisfies(row -> {
			assertThat(row.head()).isEqualTo(new StorylineId(fork.right()));
			assertThat(row.storylines()).containsExactly(new StorylineId(fork.right()));
		});
		// 씨앗 바꾸기는 사용자 신호가 아닌 이력, 사용자의 해제는 끈 갈래의 헤드 하나 — 이력을 차례로 재생하면 지금 씨앗 {right}
		assertThat(history(user)).containsExactly(List.of(fork.root(), "ADD", "USER"),
				List.of(fork.root(), "REMOVE", "STORYLINE_SPLIT"), List.of(fork.left(), "ADD", "STORYLINE_SPLIT"),
				List.of(fork.right(), "ADD", "STORYLINE_SPLIT"), List.of(fork.left(), "REMOVE", "USER"));

		// 대조 — 갈라지기 전 root 를 다시 누르면 두 갈래 모두 추적 중으로 돌아간다
		this.subscriptions.subscribe(user, new StorylineId(fork.root()), at(240));
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(fork.left()), new StorylineId(fork.right())),
				at(300)))
			.containsExactly(Map.entry(new StorylineId(fork.left()), true), Map.entry(new StorylineId(fork.right()), true));
	}

	@Test
	void theTopBeforeTheForkTracksAndUntracksEveryBranch() {
		UserId user = user();
		Fork fork = fork(user);

		this.subscriptions.subscribe(user, new StorylineId(fork.root()), at(60));

		assertThat(this.subscriptions.view(user, at(120)).subscriptions()).extracting(SubscribedUserStoryline::head)
			.containsExactlyInAnyOrder(new StorylineId(fork.left()), new StorylineId(fork.right()));

		this.subscriptions.unsubscribe(user, new StorylineId(fork.root()), at(180));

		assertThat(this.subscriptions.view(user, at(240)).subscriptions()).isEmpty();
		assertThat(this.subscriptions.states(user, List.of(new StorylineId(fork.left()), new StorylineId(fork.right())),
				at(240)))
			.containsExactly(Map.entry(new StorylineId(fork.left()), false), Map.entry(new StorylineId(fork.right()), false));
	}

	/**
	 * 측정 사용자 u1 의 갈래 — root → left · root → middle · root → right · middle → right. root 에 좋아요가 있어 헤드 left · right 의
	 * 사용자 스토리라인 둘(root 는 둘 모두에 든다)
	 */
	private Fork fork(UserId user) {
		long root = storyline();
		long left = storyline();
		long middle = storyline();
		long right = storyline();
		UserStorylineFixtures.follows(this.jdbc, root, left);
		UserStorylineFixtures.follows(this.jdbc, root, middle);
		UserStorylineFixtures.follows(this.jdbc, root, right);
		UserStorylineFixtures.follows(this.jdbc, middle, right);
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, root), "LIKE", at(30));
		return new Fork(root, left, middle, right);
	}

	private record Fork(long root, long left, long middle, long right) {
	}

	@Test
	void theLiveliestStateIsActiveThenQuietAndEndedOnlyWhenAllEnded() {
		assertThat(StorylineSubscriptions.liveliest(List.of(StorylineState.ENDED, StorylineState.QUIET)))
			.isEqualTo(StorylineState.QUIET);
		assertThat(StorylineSubscriptions
			.liveliest(List.of(StorylineState.QUIET, StorylineState.ACTIVE, StorylineState.ENDED)))
			.isEqualTo(StorylineState.ACTIVE);
		assertThat(StorylineSubscriptions.liveliest(List.of(StorylineState.ENDED, StorylineState.ENDED)))
			.isEqualTo(StorylineState.ENDED);
	}

	@Test
	void theCurrentTrackedSeedsAreTheReplayOfTheHistory() {
		UserId user = user();
		long a = storyline();
		long b = storyline();
		long c = storyline();
		this.subscriptions.subscribe(user, new StorylineId(a), NOW);
		this.subscriptions.subscribe(user, new StorylineId(b), at(60));
		this.subscriptions.subscribe(user, new StorylineId(c), at(120));
		this.subscriptions.unsubscribe(user, new StorylineId(a), at(180));
		UserStorylineFixtures.merge(this.jdbc, b, c);
		this.subscriptions.view(user, at(240));

		TreeSet<Long> replayed = new TreeSet<>();
		for (List<Object> row : history(user)) {
			if ("ADD".equals(row.get(1))) {
				replayed.add((Long) row.get(0));
			}
			else {
				replayed.remove((Long) row.get(0));
			}
		}
		TreeSet<Long> seeds = new TreeSet<>();
		saved(user).forEach(one -> seeds.addAll(one.seeds()));
		assertThat(replayed).containsExactly(c);
		assertThat(seeds).containsExactly(c);
	}

	@Test
	void followingOutsideATransactionIsRefused() {
		UserId user = user();

		assertThatExceptionOfType(IllegalTransactionStateException.class)
			.isThrownBy(() -> this.followups.followLocked(user, Map.of(), NOW));
	}

	private UserId user() {
		return UserStorylineFixtures.user(this.jdbc, "alice@example.com");
	}

	private long storyline() {
		return UserStorylineFixtures.storyline(this.jdbc, NOW);
	}

	private static Instant at(long seconds) {
		return NOW.plusSeconds(seconds);
	}

	/** 그 사용자 스토리라인의 정리 행 — 추적 대상마다 글 없는 꼭대기 행을 함께 둔다(저장 모델의 불변식) */
	private void summarize(Saved userStoryline, String title) {
		this.jdbc.sql("""
				insert into user_storyline_summaries (user_storyline_id, title, summary, recent, provider, model,
				    input_tokens, output_tokens, created_at, updated_at, version)
				values (:id, :title, '요약', '최근', 'anthropic', 'claude-sonnet-5-5', 10, 5, now(), now(), 0)
				""").param("id", userStoryline.id()).param("title", title).update();
		for (long top : userStoryline.members()) {
			this.jdbc.sql("""
					insert into user_storyline_top_summaries (user_storyline_id, storyline_id, title, summary, recent)
					values (:id, :top, '', '', '')
					""").param("id", userStoryline.id()).param("top", top).update();
		}
	}

	private List<Saved> saved(UserId user) {
		return UserStorylineFixtures.saved(this.jdbc, user);
	}

	private List<List<Object>> history(UserId user) {
		return UserStorylineFixtures.history(this.jdbc, user);
	}

}
