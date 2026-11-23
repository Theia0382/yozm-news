package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.SummaryUsage;
import net.yozmnews.user.TopSummary;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserStorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryRef;
import net.yozmnews.user.UserStorylineSummaryView;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.summary.SummaryText;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
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
 * 정리 조회 — 정리가 있는 사용자 스토리라인만, 그 사용자의 것만 낸다. 추적 대상은 저장한 추적 대상이고 꼭대기마다의 글은 그 꼭대기만 낸다.
 * 사용량은 마지막 정리의 모델 · 토큰과 시도 기록의 호출 수 · 합이다. 조회는 사용자 스토리라인을 맞추지도 쓰지도 않는다.
 * <p>
 * 지키는 규칙: docs/specs/user.md §3 의 {@code UserStorylineSummaries}. 기대값은 손으로 쓴 값이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class UserStorylineSummariesServiceTests {

	private static final Instant FIRST = Instant.parse("2026-11-20T01:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-21T01:00:00Z");

	@Autowired
	UserStorylineSummaries directory;

	@Autowired
	StorylineSummaryRepository summaries;

	@Autowired
	UserStorylineRepository userStorylines;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	UserId alice;

	UserId bob;

	@BeforeEach
	void users() {
		UserStorylineFixtures.clear(this.jdbc);
		this.alice = UserStorylineFixtures.user(this.jdbc, "alice@example.com");
		this.bob = UserStorylineFixtures.user(this.jdbc, "bob@example.com");
	}

	@AfterEach
	void clean() {
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void aSummarizedUserStorylineIsShownWithItsTextsMembersTrackingAndUsage() {
		long id = userStoryline(this.alice, Set.of(11L, 12L), true);
		summarize(id, "전체", Map.of(11L, new SummaryText("가 제목", "가 요약", "가 최근"), 12L, SummaryText.NONE), FIRST, LATER);
		this.transaction.executeWithoutResult(status -> {
			this.summaries.recordFailed(id, 300, 40, "OUTPUT_TRUNCATED", FIRST);
			this.summaries.recordStored(id, 1_200, 300);
		});

		// 헤드를 저장하기 전의 개체라 손잡이는 가장 작은 꼭대기다
		assertThat(this.directory.shown(this.alice)).containsExactly(new UserStorylineSummaryView(new UserStorylineId(id),
				new StorylineId(11), "전체 제목", "전체 요약", "전체 최근", true, Set.of(new StorylineId(11), new StorylineId(12)),
				List.of(new TopSummary(new StorylineId(11), "가 제목", "가 요약", "가 최근"),
						TopSummary.without(new StorylineId(12))),
				new SummaryUsage("ANTHROPIC", "claude-sonnet-5-5", 1_200, 300, 2, 1_500, 340), FIRST, LATER));
	}

	@Test
	void theHandleIsTheStoredHead() {
		long id = userStoryline(this.alice, Set.of(11L, 12L), true);
		this.transaction.executeWithoutResult(status -> this.userStorylines.setHead(id, 12));
		summarize(id, "전체", Map.of(11L, SummaryText.NONE, 12L, SummaryText.NONE), FIRST, LATER);

		// 대조 — 저장한 헤드가 가장 작은 꼭대기보다 앞선다
		assertThat(this.directory.shown(this.alice)).singleElement()
			.extracting(UserStorylineSummaryView::head)
			.isEqualTo(new StorylineId(12));
	}

	@Test
	void topTextsFollowTheStoredTrackedTargetsNotTheTopsAtTheLastSummary() {
		long id = userStoryline(this.alice, Set.of(11L, 13L), false);
		summarize(id, "전체", Map.of(11L, new SummaryText("가 제목", "가 요약", "가 최근"), 12L,
				new SummaryText("빠진 꼭대기", "요약", "최근")), FIRST, FIRST);

		UserStorylineSummaryView view = this.directory.find(this.alice, new UserStorylineId(id)).orElseThrow();

		assertThat(view.members()).containsExactlyInAnyOrder(new StorylineId(11), new StorylineId(13));
		assertThat(view.tops()).containsExactly(new TopSummary(new StorylineId(11), "가 제목", "가 요약", "가 최근"),
				TopSummary.without(new StorylineId(13)));
		assertThat(view.tracked()).isFalse();
		// 시도 기록이 없으면 호출 수와 합은 0 이다
		assertThat(view.usage()).isEqualTo(new SummaryUsage("ANTHROPIC", "claude-sonnet-5-5", 1_200, 300, 0, 0, 0));
	}

	@Test
	void onlySummarizedUserStorylinesAreShownLatestFirst() {
		long older = userStoryline(this.alice, Set.of(11L), false);
		long newer = userStoryline(this.alice, Set.of(21L), false);
		long unsummarized = userStoryline(this.alice, Set.of(31L), true);
		summarize(older, "앞", Map.of(11L, new SummaryText("앞", "요약", "최근")), FIRST, FIRST);
		summarize(newer, "뒤", Map.of(21L, new SummaryText("뒤", "요약", "최근")), FIRST, LATER);

		assertThat(this.directory.shown(this.alice)).extracting(UserStorylineSummaryView::id)
			.containsExactly(new UserStorylineId(newer), new UserStorylineId(older));
		assertThat(this.directory.find(this.alice, new UserStorylineId(unsummarized))).isEmpty();
	}

	@Test
	void aRemovedSummaryIsNoLongerShown() {
		long id = userStoryline(this.alice, Set.of(11L), false);
		summarize(id, "전체", Map.of(11L, new SummaryText("가", "요약", "최근")), FIRST, FIRST);
		assertThat(this.directory.shown(this.alice)).hasSize(1);

		this.transaction.executeWithoutResult(status -> this.summaries.delete(id));

		assertThat(this.directory.shown(this.alice)).isEmpty();
		assertThat(this.directory.find(this.alice, new UserStorylineId(id))).isEmpty();
		assertThat(this.directory.ofTops(this.alice, List.of(new StorylineId(11)))).isEmpty();
	}

	@Test
	void anotherUsersSummariesAreNeverGiven() {
		long alices = userStoryline(this.alice, Set.of(11L), false);
		long bobs = userStoryline(this.bob, Set.of(11L, 21L), false);
		summarize(alices, "앨리스", Map.of(11L, new SummaryText("가", "요약", "최근")), FIRST, FIRST);
		summarize(bobs, "밥", Map.of(11L, new SummaryText("나", "요약", "최근"), 21L, new SummaryText("다", "요약", "최근")),
				FIRST, FIRST);

		assertThat(this.directory.shown(this.alice)).extracting(UserStorylineSummaryView::id)
			.containsExactly(new UserStorylineId(alices));
		assertThat(this.directory.find(this.alice, new UserStorylineId(bobs))).isEmpty();
		assertThat(this.directory.ofTops(this.alice, List.of(new StorylineId(11), new StorylineId(21))))
			.isEqualTo(Map.of(new StorylineId(11), new UserStorylineSummaryRef(new UserStorylineId(alices), "앨리스 제목")));
	}

	@Test
	void topsAreMappedToTheSummarizedUserStorylineThatTracksThem() {
		long two = userStoryline(this.alice, Set.of(11L, 12L), false);
		long one = userStoryline(this.alice, Set.of(21L), false);
		userStoryline(this.alice, Set.of(31L), true);
		summarize(two, "둘", Map.of(11L, new SummaryText("가", "요약", "최근"), 12L, SummaryText.NONE), FIRST, FIRST);
		summarize(one, "하나", Map.of(21L, new SummaryText("나", "요약", "최근")), FIRST, FIRST);

		assertThat(this.directory.ofTops(this.alice,
				List.of(new StorylineId(12), new StorylineId(21), new StorylineId(31), new StorylineId(99), new StorylineId(12))))
			.isEqualTo(Map.of(new StorylineId(12), new UserStorylineSummaryRef(new UserStorylineId(two), "둘 제목"),
					new StorylineId(21), new UserStorylineSummaryRef(new UserStorylineId(one), "하나 제목")));
		assertThat(this.directory.ofTops(this.alice, List.of())).isEmpty();
	}

	@Test
	void moreThanAHundredTopsAreRefused() {
		List<StorylineId> hundred = new ArrayList<>();
		for (long id = 1; id <= 100; id++) {
			hundred.add(new StorylineId(id));
		}
		List<StorylineId> over = new ArrayList<>(hundred);
		over.add(new StorylineId(101));

		assertThat(this.directory.ofTops(this.alice, hundred)).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> this.directory.ofTops(this.alice, over));
	}

	@Test
	void readingWritesNothing() {
		long id = userStoryline(this.alice, Set.of(11L), false);
		summarize(id, "전체", Map.of(11L, new SummaryText("가", "요약", "최근")), FIRST, FIRST);
		String before = rows();

		this.directory.shown(this.alice);
		this.directory.find(this.alice, new UserStorylineId(id));
		this.directory.ofTops(this.alice, List.of(new StorylineId(11)));

		assertThat(rows()).isEqualTo(before);
		assertThat(this.jdbc.sql("select count(*) from user_settings").query(Integer.class).single()).isZero();
	}

	private long userStoryline(UserId user, Set<Long> members, boolean tracked) {
		return this.transaction.execute(status -> {
			long id = this.userStorylines.create(user, FIRST);
			this.userStorylines.replaceMembers(id, members);
			if (tracked) {
				this.userStorylines.subscribe(user, id, FIRST);
			}
			return id;
		});
	}

	private void summarize(long id, String name, Map<Long, SummaryText> tops, Instant createdAt, Instant updatedAt) {
		this.transaction.executeWithoutResult(
				status -> this.summaries.create(new StorylineSummary(id, new SummaryText(name + " 제목", name + " 요약", name + " 최근"),
						tops, Map.of(101L, 1_001L), "ANTHROPIC", "claude-sonnet-5-5", 1_200, 300, createdAt, updatedAt, 0)));
	}

	private String rows() {
		return this.jdbc.sql("""
				select (select coalesce(string_agg(u::text, '|' order by id), '') from user_storylines u)
				    || '#' || (select coalesce(string_agg(m::text, '|' order by user_storyline_id, storyline_id), '')
				               from user_storyline_members m)
				    || '#' || (select coalesce(string_agg(s::text, '|' order by user_storyline_id), '')
				               from user_storyline_summaries s)
				""").query(String.class).single();
	}

}
