package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.InterestSignalWindow;
import net.yozmnews.user.UserStorylineDirectory;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserStorylineFixtures.Saved;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 사용자 스토리라인의 소비자 조회 — 쓰지 않고, 저장하는 따라가기와 같은 결과(추적 대상 · 추적 여부)를 낸다. 창을 넓히면 그 창의 신호가 만든
 * 묶음이 더 나온다. 실제 PostgreSQL 에서 본다 — 스토리라인과 관계는 행으로 직접 만든다.
 * <p>
 * 지키는 규칙: 사용자 스토리라인의 소비자 조회(docs/specs/user.md §3 · §5)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class UserStorylineDirectoryServiceTests {

	private static final Instant TRACKED = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	UserStorylineDirectory directory;

	@Autowired
	StorylineSubscriptions subscriptions;

	@Autowired
	InterestSignalWindow window;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void readingGivesWhatFollowingWouldSaveAndWritesNothing() {
		UserId user = UserStorylineFixtures.user(this.jdbc, "alice@example.com");
		long earlier = storyline();
		long later = storyline();
		long reacted = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		this.subscriptions.subscribe(user, new StorylineId(earlier), TRACKED);
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, later), "LIKE",
				TRACKED.plusSeconds(60));
		this.subscriptions.view(user, TRACKED.plusSeconds(120));
		// 저장한 뒤에 바뀐 것 — 이어짐이 사라져 흐름이 갈리고, 다른 흐름에 싫어요가 생겼다
		UserStorylineFixtures.unrelate(this.jdbc, earlier, later);
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, reacted), "DISLIKE",
				TRACKED.plusSeconds(180));
		List<Saved> before = UserStorylineFixtures.saved(this.jdbc, user);
		Instant now = TRACKED.plusSeconds(240);

		List<UserStorylineView> read = this.directory.of(user, this.window.start(now), now);

		// 쓰지 않는다 — 저장 행은 그대로다(갈라지기 전의 하나)
		assertThat(UserStorylineFixtures.saved(this.jdbc, user)).isEqualTo(before);
		assertThat(before).singleElement().extracting(Saved::members).isEqualTo(Set.of(earlier, later));
		// 갈라진 조각은 추적을 물려받고, 반응만 받은 흐름은 추적 중이 아니다 — 아직 저장되지 않은 묶음은 ID 가 없다
		assertThat(read).containsExactly(
				new UserStorylineView(new UserStorylineId(before.getFirst().id()), Set.of(new StorylineId(earlier)), true),
				new UserStorylineView(null, Set.of(new StorylineId(later)), true),
				new UserStorylineView(null, Set.of(new StorylineId(reacted)), false));

		// 저장하는 따라가기 뒤에 다시 읽어도 추적 대상과 추적 여부가 같다
		this.subscriptions.view(user, now);
		List<UserStorylineView> after = this.directory.of(user, this.window.start(now), now);
		assertThat(after).extracting(UserStorylineView::tops, UserStorylineView::tracked)
			.containsExactlyElementsOf(
					read.stream().map(view -> org.assertj.core.groups.Tuple.tuple(view.tops(), view.tracked())).toList());
		assertThat(after).allMatch(view -> view.id() != null);
	}

	@Test
	void aWiderWindowGivesTheFlowsOfOlderSignals() {
		UserId user = UserStorylineFixtures.user(this.jdbc, "alice@example.com");
		long old = storyline();
		UserStorylineFixtures.reaction(this.jdbc, user, UserStorylineFixtures.eventIn(this.jdbc, old), "LIKE", TRACKED);
		Instant now = TRACKED.plus(Duration.ofDays(200));

		// 대조 — 지금 기준의 창에는 그 반응이 없다
		assertThat(this.directory.of(user, this.window.start(now), now)).isEmpty();
		assertThat(this.directory.of(user, TRACKED, now))
			.containsExactly(new UserStorylineView(null, Set.of(new StorylineId(old)), false));
	}

	@Test
	void aWindowWhoseStartIsAfterItsEndIsRefused() {
		UserId user = UserStorylineFixtures.user(this.jdbc, "alice@example.com");

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.directory.of(user, TRACKED.plusSeconds(1), TRACKED));
	}

	private long storyline() {
		return UserStorylineFixtures.storyline(this.jdbc, TRACKED);
	}

}
