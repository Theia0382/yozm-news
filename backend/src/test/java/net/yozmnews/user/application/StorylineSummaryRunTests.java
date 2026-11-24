package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.DigestModelDirectory;
import net.yozmnews.user.StorylineStanding;
import net.yozmnews.user.StorylineStandingNow;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.adapter.out.fake.FakeStorylineSummarizer;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.summary.SummaryAttempt;
import net.yozmnews.user.domain.summary.SummaryFailure;
import net.yozmnews.user.domain.summary.SummaryOutcome;
import net.yozmnews.user.domain.summary.SummaryRequest;
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
 * 정리 실행 — 실제 PostgreSQL 위에서 사용자 스토리라인을 맞춰 저장하고, 흐름 판정대로 만들 흐름만 정리해 저장한다. 생성기는 로컬 대체물을 감싼
 * 대역이다(호출을 세고 대본대로 실패한다). 흐름 판정은 한 시험만 실제 구현({@code digest})으로, 나머지는 꼭대기 집합을 그대로 답하는 대역으로
 * 준다 — 판정 자체의 시험은 {@code StorylineStandingServiceTests} 가 한다.
 * <p>
 * 지키는 규칙: docs/specs/user.md §5 의 정리 실행. 기대값은 손으로 쓴 행 · 글이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineSummaryRunTests {

	private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

	private static final Instant REACTED = NOW.minus(Duration.ofHours(1));

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 200_000, true);

	@Autowired
	InterestSettingsLock lock;

	@Autowired
	UserStorylineFollowups followups;

	@Autowired
	UserStorylineRepository userStorylines;

	@Autowired
	StorylineSummaryRepository summaries;

	@Autowired
	SummaryMaterials materials;

	@Autowired
	StorylineStandingNow realStanding;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private InterestRows rows;

	private UserId user;

	private final Script script = new Script();

	@BeforeEach
	void prepare() {
		this.rows = new InterestRows(this.jdbc);
		clean();
		this.user = UserStorylineFixtures.user(this.jdbc, "summary@example.com");
	}

	@AfterEach
	void clean() {
		this.rows.clear();
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void aFlowTheUserOnlyReactedToIsSavedAndSummarizedWithTheRealStanding() {
		Instant realNow = Instant.now();
		long storyline = UserStorylineFixtures.storyline(this.jdbc, realNow);
		long first = this.rows.article("앞 사건", "politics.legislature");
		long second = this.rows.article("뒤 사건", "politics.legislature");
		link(storyline, first);
		link(storyline, second);
		this.rows.cite(this.user.value(), realNow.minus(Duration.ofDays(1)), first, second);
		this.rows.react(this.user.value(), first, "LIKE", realNow.minus(Duration.ofHours(2)));
		this.rows.react(this.user.value(), second, "LIKE", realNow.minus(Duration.ofHours(2)));
		assertThat(count("user_storylines")).isZero();
		String storylinesBefore = storylineRows();

		SummaryRunResult result = run(this.realStanding, realNow).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(1, 0, 0, 0));
		assertThat(this.summaries.load(this.user)).singleElement().satisfies(summary -> {
			assertThat(summary.tops().keySet()).containsExactly(storyline);
			assertThat(summary.events()).isEqualTo(Map.of(eventOf(first), first, eventOf(second), second));
			assertThat(summary.whole().summary()).isEqualTo("사건 2건을 정리했다.");
			assertThat(summary.whole().title()).startsWith(FakeStorylineSummarizer.MARK);
			assertThat(List.of(summary.provider(), summary.model())).containsExactly("anthropic", "claude-sonnet-5-5");
			assertThat(summary.version()).isZero();
			assertThat(summary.createdAt()).isEqualTo(summary.updatedAt());
		});
		assertThat(count("user_storylines")).isEqualTo(1);
		assertThat(this.summaries.attempts(this.user)).singleElement()
			.satisfies(attempt -> assertThat(List.of(attempt.calls(), attempt.failures())).containsExactly(1, 0));
		assertThat(storylineRows()).isEqualTo(storylinesBefore);
	}

	@Test
	void aSecondRunWithoutNewEventsCallsNothing() {
		long storyline = flow("가", 2);
		StorylineSummaryRun run = run(followed(storyline), NOW);
		run.run(this.user);

		assertThat(run.run(this.user)).isEqualTo(SummaryRunResult.NOTHING);

		assertThat(this.script.calls).isEqualTo(1);
		assertThat(this.summaries.load(this.user)).extracting(StorylineSummary::version).containsExactly(0);
	}

	@Test
	void aNewEventExtendsOnlyItsFlowWithOnlyThatEvent() {
		long changed = flow("가", 2);
		long unchanged = flow("나", 1);
		run(followed(changed, unchanged), NOW).run(this.user);
		long added = this.rows.article("가 새 사건", "politics.legislature");
		link(changed, added);

		SummaryRunResult result = run(followed(changed, unchanged), NOW.plusSeconds(60)).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(1, 0, 0, 0));
		assertThat(this.script.calls).isEqualTo(3);
		assertThat(this.script.requests.getLast().input().tops())
			.satisfies(tops -> assertThat(tops.get(changed)).extracting(event -> event.articleId()).containsExactly(added));
		StorylineSummary extended = summaryOf(changed);
		assertThat(extended.whole())
			.isEqualTo(new SummaryText("[로컬 대체물] 가 새 사건", "지난 정리에 새 사건 1건을 더했다.", "가장 최근 사건: 가 새 사건"));
		assertThat(extended.version()).isEqualTo(1);
		assertThat(extended.events()).hasSize(3).containsEntry(eventOf(added), added);
		assertThat(extended.updatedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(extended.createdAt()).isEqualTo(NOW);
		assertThat(summaryOf(unchanged).version()).isZero();
	}

	@Test
	void changedTrackedTargetsRebuildTheSummaryFromScratchWithATextForEachTop() {
		long earlier = flow("가", 1);
		run(followed(earlier), NOW).run(this.user);
		long later = UserStorylineFixtures.storyline(this.jdbc, NOW);
		link(later, this.rows.article("나 사건 1", "politics.legislature"));
		UserStorylineFixtures.follows(this.jdbc, earlier, later);

		// 관심 흐름은 사용자 스토리라인의 값이라 그 꼭대기 모두에 든다 — 이어진 뒤 흐름 later 가 헤드다
		SummaryRunResult result = run(followed(earlier, later), NOW.plusSeconds(60)).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(1, 0, 0, 0));
		assertThat(this.script.requests.getLast().input().previousWhole()).isNull();
		StorylineSummary rebuilt = summaryOf(earlier);
		assertThat(rebuilt.tops()).isEqualTo(Map.of(earlier,
				new SummaryText("[로컬 대체물] 가 사건 1", "사건 1건을 정리했다.", "가장 최근 사건: 가 사건 1"), later,
				new SummaryText("[로컬 대체물] 나 사건 1", "사건 1건을 정리했다.", "가장 최근 사건: 나 사건 1")));
		assertThat(rebuilt.whole().summary()).isEqualTo("사건 2건을 정리했다.");
		assertThat(rebuilt.events()).hasSize(2);
		assertThat(rebuilt.version()).isEqualTo(1);
		assertThat(rebuilt.createdAt()).isEqualTo(NOW);
		assertThat(rebuilt.updatedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void aTopWithoutEventsIsLeftOutOfTheCallAndKeptWithoutATextUntilItGetsAnEvent() {
		long first = flow("가", 1);
		long empty = UserStorylineFixtures.storyline(this.jdbc, NOW);
		UserStorylineFixtures.follows(this.jdbc, first, empty);

		// 관심 흐름은 사용자 스토리라인의 값이라 그 꼭대기 모두에 든다 — 이어진 뒤 흐름 empty 가 헤드다
		assertThat(run(followed(first, empty), NOW).run(this.user)).isEqualTo(new SummaryRunResult(1, 0, 0, 0));

		SummaryRequest created = this.script.requests.getLast();
		assertThat(created.prompt().aliases()).containsExactly("T1");
		assertThat(created.input().topIds()).containsExactly(first);
		assertThat(created.prompt().userContent()).startsWith("{\"previous\":null,\"flows\":[[\"T1\",null,[[")
			.doesNotContain("T2");
		StorylineSummary stored = summaryOf(first);
		assertThat(stored.tops().keySet()).containsExactlyInAnyOrder(first, empty);
		assertThat(stored.tops().get(empty)).isEqualTo(SummaryText.NONE);
		assertThat(stored.tops().get(first))
			.isEqualTo(new SummaryText("[로컬 대체물] 가 사건 1", "사건 1건을 정리했다.", "가장 최근 사건: 가 사건 1"));
		assertThat(stored.whole()).isEqualTo(stored.tops().get(first));

		long added = this.rows.article("나 새 사건", "politics.legislature");
		link(empty, added);

		assertThat(run(followed(first, empty), NOW.plusSeconds(60)).run(this.user))
			.isEqualTo(new SummaryRunResult(1, 0, 0, 0));

		SummaryRequest extended = this.script.requests.getLast();
		assertThat(extended.prompt().aliases()).containsExactly("T1", "T2");
		assertThat(extended.input().previousTops().keySet()).containsExactly(first);
		StorylineSummary after = summaryOf(first);
		assertThat(after.tops().get(first))
			.isEqualTo(new SummaryText("[로컬 대체물] 가 사건 1", "사건 1건을 정리했다.", "가장 최근 사건: 가 사건 1"));
		assertThat(after.tops().get(empty))
			.isEqualTo(new SummaryText("[로컬 대체물] 나 새 사건", "지난 정리에 새 사건 1건을 더했다.", "가장 최근 사건: 나 새 사건"));
		assertThat(after.version()).isEqualTo(1);
	}

	@Test
	void aFlowNoLongerFollowedOrKeptLosesItsSummaryAndKeepsItsAttempts() {
		long storyline = flow("가", 1);
		run(followed(storyline), NOW).run(this.user);

		SummaryRunResult result = run(user -> Optional.of(new StorylineStanding(Set.of(), Set.of())), NOW).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(0, 1, 0, 0));
		assertThat(this.summaries.load(this.user)).isEmpty();
		assertThat(this.summaries.attempts(this.user)).extracting(SummaryAttempt::calls).containsExactly(1);
	}

	@Test
	void aKeptFlowKeepsItsSummaryButAKeptFlowWithoutOneIsNotCreated() {
		long summarized = flow("가", 1);
		run(followed(summarized), NOW).run(this.user);
		long keptOnly = flow("나", 1);
		StorylineStandingNow kept = user -> Optional
			.of(new StorylineStanding(Set.of(), Set.of(new StorylineId(summarized), new StorylineId(keptOnly))));

		assertThat(run(kept, NOW).run(this.user)).isEqualTo(SummaryRunResult.NOTHING);

		assertThat(this.summaries.load(this.user)).extracting(summary -> summary.tops().keySet())
			.containsExactly(Set.of(summarized));
	}

	@Test
	void withoutAStandingNothingIsRemoved() {
		long storyline = flow("가", 1);
		run(followed(storyline), NOW).run(this.user);

		assertThat(run(user -> Optional.empty(), NOW).run(this.user)).isEqualTo(SummaryRunResult.NOTHING);

		assertThat(this.summaries.load(this.user)).hasSize(1);
	}

	@Test
	void aFailedCallIsRecordedTheOtherFlowIsStoredAndTheFailedFlowRests() {
		long failing = flow("가", 1);
		long working = flow("나", 1);
		this.script.script = request -> request.input().topIds().contains(failing)
				? new SummaryOutcome.Failed(SummaryFailure.PROVIDER_UNAVAILABLE) : this.script.fake.summarize(request);

		SummaryRunResult result = run(followed(failing, working), NOW).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(1, 0, 1, 0));
		assertThat(this.summaries.load(this.user)).extracting(summary -> summary.tops().keySet())
			.containsExactly(Set.of(working));
		assertThat(this.summaries.attempts(this.user))
			.extracting(SummaryAttempt::calls, SummaryAttempt::totalInputTokens, SummaryAttempt::failures,
					SummaryAttempt::failureCode, SummaryAttempt::failedAt)
			.contains(tuple(1, 0L, 1, "PROVIDER_UNAVAILABLE", NOW));

		this.script.script = this.script.fake::summarize;
		assertThat(run(followed(failing, working), NOW.plus(Duration.ofDays(1)).minusSeconds(1)).run(this.user))
			.isEqualTo(SummaryRunResult.NOTHING);
		assertThat(run(followed(failing, working), NOW.plus(Duration.ofDays(1))).run(this.user))
			.isEqualTo(new SummaryRunResult(1, 0, 0, 0));
		assertThat(this.summaries.load(this.user)).hasSize(2);
		// 저장하면 연속 실패가 0 으로 돌아간다 — 호출은 실패 하나와 저장 하나
		assertThat(this.summaries.attempts(this.user))
			.extracting(SummaryAttempt::calls, SummaryAttempt::failures, SummaryAttempt::failureCode)
			.containsExactlyInAnyOrder(tuple(2, 0, null), tuple(1, 0, null));
	}

	@Test
	void aKeySideFailureStopsTheRun() {
		long first = flow("가", 1);
		long second = flow("나", 1);
		this.script.script = request -> new SummaryOutcome.Failed(SummaryFailure.INVALID_KEY);

		SummaryRunResult result = run(followed(first, second), NOW).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(0, 0, 1, 0));
		assertThat(this.script.calls).isEqualTo(1);
		assertThat(this.summaries.attempts(this.user)).extracting(SummaryAttempt::failureCode)
			.containsExactly("INVALID_KEY");
	}

	@Test
	void aMissingKeyStopsTheRunWithoutAnAttempt() {
		long first = flow("가", 1);
		long second = flow("나", 1);
		this.script.script = request -> new SummaryOutcome.KeyMissing();

		assertThat(run(followed(first, second), NOW).run(this.user)).isEqualTo(SummaryRunResult.NOTHING);

		assertThat(this.script.calls).isEqualTo(1);
		assertThat(this.summaries.attempts(this.user)).isEmpty();
		assertThat(this.summaries.load(this.user)).isEmpty();
	}

	@Test
	void unreadableTruncatedAndRefusedAnswersAreFailuresThatCountTheirTokens() {
		long unreadable = flow("가", 1);
		long truncated = flow("나", 1);
		long refused = flow("다", 1);
		this.script.script = request -> {
			List<Long> tops = request.input().topIds();
			if (tops.contains(unreadable)) {
				return new SummaryOutcome.Generated("정리했습니다.", 100, 10, GenerationStop.COMPLETE);
			}
			if (tops.contains(truncated)) {
				return new SummaryOutcome.Generated("{\"whole\":{\"title\":\"잘", 200, 20, GenerationStop.TRUNCATED);
			}
			return new SummaryOutcome.Generated("", 300, 0, GenerationStop.REFUSED);
		};

		SummaryRunResult result = run(followed(unreadable, truncated, refused), NOW).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(0, 0, 3, 0));
		assertThat(this.summaries.load(this.user)).isEmpty();
		assertThat(this.summaries.attempts(this.user))
			.extracting(SummaryAttempt::totalInputTokens, SummaryAttempt::totalOutputTokens, SummaryAttempt::failures,
					SummaryAttempt::failureCode)
			.containsExactlyInAnyOrder(tuple(100L, 10L, 1, "UNREADABLE_OUTPUT"),
					tuple(200L, 20L, 1, "OUTPUT_TRUNCATED"),
					tuple(300L, 0L, 1, "OUTPUT_REFUSED"));
	}

	@Test
	void aResultIsDiscardedWhenTheTrackedTargetsChangeDuringTheCall() {
		long storyline = flow("가", 1);
		long other = UserStorylineFixtures.storyline(this.jdbc, NOW);
		this.script.script = request -> {
			this.jdbc.sql("""
					insert into user_storyline_members (user_storyline_id, storyline_id)
					select id, :other from user_storylines where user_id = :user
					""").param("other", other).param("user", this.user.value()).update();
			return this.script.fake.summarize(request);
		};

		SummaryRunResult result = run(followed(storyline), NOW).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(0, 0, 0, 1));
		assertThat(this.summaries.load(this.user)).isEmpty();
		assertThat(this.summaries.attempts(this.user)).singleElement().satisfies(attempt -> {
			assertThat(List.of(attempt.calls(), attempt.failures())).containsExactly(1, 0);
			assertThat(attempt.totalInputTokens()).isPositive();
		});
	}

	@Test
	void aResultForAUserStorylineThatVanishedIsDroppedWithoutAnAttempt() {
		long storyline = flow("가", 1);
		this.script.script = request -> {
			this.jdbc.sql("delete from user_storylines where user_id = :user").param("user", this.user.value()).update();
			return this.script.fake.summarize(request);
		};

		SummaryRunResult result = run(followed(storyline), NOW).run(this.user);

		assertThat(result).isEqualTo(new SummaryRunResult(0, 0, 0, 1));
		assertThat(count("user_storyline_summaries") + count("user_storyline_summary_attempts")).isZero();
	}

	@Test
	void withoutAUsableModelNothingIsSavedOrCalled() {
		flow("가", 1);
		StorylineSummaryRun run = new StorylineSummaryRun(user -> Optional.empty(), this.lock, this.followups,
				this.userStorylines, this.summaries, user -> Optional.of(new StorylineStanding(Set.of(), Set.of())),
				this.materials, this.script, this.transaction, InstantSource.fixed(NOW));

		assertThat(run.run(this.user)).isEqualTo(SummaryRunResult.NOTHING);

		assertThat(this.script.calls).isZero();
		assertThat(count("user_storylines")).isZero();
	}

	/** 스토리라인 하나와 그 스토리라인에 든 사건들 — 사건마다 대표 기사 하나, 사용자가 첫 사건에 좋아요를 눌렀다 */
	private long flow(String name, int events) {
		long storyline = UserStorylineFixtures.storyline(this.jdbc, NOW);
		for (int i = 1; i <= events; i++) {
			long article = this.rows.article(name + " 사건 " + i, "politics.legislature");
			link(storyline, article);
			if (i == 1) {
				UserStorylineFixtures.reaction(this.jdbc, this.user, eventOf(article), "LIKE", REACTED);
			}
		}
		return storyline;
	}

	private void link(long storyline, long article) {
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, :event, 'MERGE', now())
				""").param("storyline", storyline).param("event", eventOf(article)).update();
	}

	private long eventOf(long article) {
		return this.jdbc.sql("select event_id from event_articles where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

	private StorylineSummary summaryOf(long storyline) {
		return this.summaries.load(this.user)
			.stream()
			.filter(summary -> summary.tops().containsKey(storyline))
			.findFirst()
			.orElseThrow();
	}

	private static StorylineStandingNow followed(long... storylines) {
		Set<StorylineId> tops = new java.util.HashSet<>();
		for (long storyline : storylines) {
			tops.add(new StorylineId(storyline));
		}
		return user -> Optional.of(new StorylineStanding(tops, Set.of()));
	}

	private StorylineSummaryRun run(StorylineStandingNow standing, Instant now) {
		DigestModelDirectory models = user -> Optional.of(MODEL);
		return new StorylineSummaryRun(models, this.lock, this.followups, this.userStorylines, this.summaries, standing,
				this.materials, this.script, this.transaction, InstantSource.fixed(now));
	}

	private int count(String table) {
		return this.jdbc.sql("select count(*) from " + table).query(Integer.class).single();
	}

	/** 공유 스토리라인 쪽 표의 내용 — 정리 실행이 건드리지 않음을 견준다 */
	private String storylineRows() {
		return this.jdbc.sql("""
				select (select coalesce(string_agg(s::text, '|' order by id), '') from storylines s)
				    || '#' || (select coalesce(string_agg(m::text, '|' order by storyline_id, event_id), '')
				               from storyline_members m)
				    || '#' || (select count(*) from storyline_relations)
				""").query(String.class).single();
	}

	/** 로컬 대체물을 감싼 생성기 대역 — 호출을 세고 요청을 모으며 대본이 있으면 대본대로 답한다 */
	private static final class Script implements StorylineSummarizer {

		private final FakeStorylineSummarizer fake = new FakeStorylineSummarizer();

		private final List<SummaryRequest> requests = new ArrayList<>();

		private int calls;

		private Function<SummaryRequest, SummaryOutcome> script = this.fake::summarize;

		@Override
		public SummaryOutcome summarize(SummaryRequest request) {
			this.calls++;
			this.requests.add(request);
			return this.script.apply(request);
		}

	}

}
