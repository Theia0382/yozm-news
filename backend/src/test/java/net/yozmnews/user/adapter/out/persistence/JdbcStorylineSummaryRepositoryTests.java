package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.SummaryAttempt;
import net.yozmnews.user.domain.summary.SummaryText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 정리 저장소 — 정리는 사용자 스토리라인마다 하나이고 판을 견줘 고쳐 쓴다. 사용자 스토리라인을 지우면 정리 · 꼭대기마다의 글 · 넣은 사건 · 시도
 * 기록이 함께 지워지고, 정리를 지워도 시도 기록은 남는다. 조회는 그 사용자의 것만 낸다.
 * <p>
 * 지키는 규칙: docs/specs/user.md §6 의 정리 표 넷
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcStorylineSummaryRepository.class, JdbcUserStorylines.class })
class JdbcStorylineSummaryRepositoryTests {

	private static final Instant FIRST = Instant.parse("2026-11-20T01:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-21T01:00:00Z");

	private static final SummaryText WHOLE = new SummaryText("전체 제목", "전체 요약", "전체 최근");

	@Autowired
	JdbcStorylineSummaryRepository summaries;

	@Autowired
	JdbcUserStorylines userStorylines;

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

	@Test
	void aCreatedSummaryIsReadBackWithItsPartsAndVersionZero() {
		long id = this.userStorylines.create(this.alice, FIRST);
		StorylineSummary summary = new StorylineSummary(id, WHOLE,
				Map.of(11L, new SummaryText("가 제목", "가 요약", "가 최근"), 12L, new SummaryText("나 제목", "나 요약", "나 최근")),
				Map.of(101L, 1001L, 102L, 1002L), "ANTHROPIC", "claude-sonnet-5-5", 1200, 300, FIRST, FIRST, 7);

		assertThat(this.summaries.create(summary)).isTrue();

		assertThat(this.summaries.load(this.alice)).containsExactly(new StorylineSummary(id, WHOLE,
				Map.of(11L, new SummaryText("가 제목", "가 요약", "가 최근"), 12L, new SummaryText("나 제목", "나 요약", "나 최근")),
				Map.of(101L, 1001L, 102L, 1002L), "ANTHROPIC", "claude-sonnet-5-5", 1200, 300, FIRST, FIRST, 0));
	}

	@Test
	void creatingOverAnExistingSummaryWritesNothing() {
		long id = this.userStorylines.create(this.alice, FIRST);
		this.summaries.create(summary(id, "처음", FIRST));

		assertThat(this.summaries.create(summary(id, "다시", LATER))).isFalse();

		assertThat(this.summaries.find(this.alice, id)).map(found -> found.whole().title()).contains("처음");
		assertThat(count("user_storyline_top_summaries")).isEqualTo(1);
	}

	@Test
	void replacingWithTheReadVersionRewritesEverythingButTheFirstTime() {
		long id = this.userStorylines.create(this.alice, FIRST);
		this.summaries.create(summary(id, "처음", FIRST));
		StorylineSummary next = new StorylineSummary(id, new SummaryText("새 제목", "새 요약", "새 최근"),
				Map.of(21L, new SummaryText("다 제목", "다 요약", "다 최근")), Map.of(201L, 2001L, 202L, 2002L, 203L, 2003L),
				"OPENAI", "gpt-next", 40, 9, LATER, LATER, 0);

		assertThat(this.summaries.replace(next, 0)).isTrue();

		assertThat(this.summaries.find(this.alice, id)).contains(new StorylineSummary(id,
				new SummaryText("새 제목", "새 요약", "새 최근"), Map.of(21L, new SummaryText("다 제목", "다 요약", "다 최근")),
				Map.of(201L, 2001L, 202L, 2002L, 203L, 2003L), "OPENAI", "gpt-next", 40, 9, FIRST, LATER, 1));
	}

	@Test
	void replacingWithAStaleVersionOrWithoutASummaryWritesNothing() {
		long id = this.userStorylines.create(this.alice, FIRST);
		long bare = this.userStorylines.create(this.alice, FIRST);
		this.summaries.create(summary(id, "처음", FIRST));
		this.summaries.replace(summary(id, "둘째", LATER), 0);

		assertThat(this.summaries.replace(summary(id, "낡은 판", LATER), 0)).isFalse();
		assertThat(this.summaries.replace(summary(bare, "없는 정리", LATER), 0)).isFalse();

		assertThat(this.summaries.load(this.alice)).extracting(found -> found.whole().title(), StorylineSummary::version)
			.containsExactly(tuple("둘째", 1));
	}

	@Test
	void deletingTheUserStorylineRemovesTheSummaryItsPartsAndTheAttempts() {
		long id = this.userStorylines.create(this.alice, FIRST);
		this.summaries.create(summary(id, "처음", FIRST));
		this.summaries.recordStored(id, 10, 2);

		this.userStorylines.delete(id);

		assertThat(List.of(count("user_storyline_summaries"), count("user_storyline_top_summaries"),
				count("user_storyline_summary_events"), count("user_storyline_summary_attempts")))
			.containsExactly(0, 0, 0, 0);
	}

	@Test
	void deletingTheSummaryKeepsTheAttempts() {
		long id = this.userStorylines.create(this.alice, FIRST);
		this.summaries.create(summary(id, "처음", FIRST));
		this.summaries.recordStored(id, 10, 2);

		this.summaries.delete(id);

		assertThat(List.of(count("user_storyline_summaries"), count("user_storyline_top_summaries"),
				count("user_storyline_summary_events")))
			.containsExactly(0, 0, 0);
		assertThat(this.summaries.attempts(this.alice)).containsExactly(new SummaryAttempt(id, 1, 10, 2, 0, null, null));
	}

	@Test
	void attemptsAddEveryCallAndCountConsecutiveFailuresUntilAStoredCall() {
		long id = this.userStorylines.create(this.alice, FIRST);

		this.summaries.recordFailed(id, 0, 0, "PROVIDER_UNAVAILABLE", FIRST);
		this.summaries.recordFailed(id, 500, 80, "OUTPUT_TRUNCATED", LATER);
		assertThat(this.summaries.attempts(this.alice))
			.containsExactly(new SummaryAttempt(id, 2, 500, 80, 2, "OUTPUT_TRUNCATED", LATER));

		this.summaries.recordDiscarded(id, 300, 40);
		assertThat(this.summaries.attempts(this.alice))
			.containsExactly(new SummaryAttempt(id, 3, 800, 120, 2, "OUTPUT_TRUNCATED", LATER));

		this.summaries.recordStored(id, 200, 30);
		assertThat(this.summaries.attempts(this.alice)).containsExactly(new SummaryAttempt(id, 4, 1000, 150, 0, null, null));
	}

	@Test
	void theFirstRecordedCallOfEachKindStartsTheRow() {
		long stored = this.userStorylines.create(this.alice, FIRST);
		long discarded = this.userStorylines.create(this.alice, FIRST);
		long failed = this.userStorylines.create(this.alice, FIRST);

		this.summaries.recordStored(stored, 5, 1);
		this.summaries.recordDiscarded(discarded, 6, 2);
		this.summaries.recordFailed(failed, 7, 3, "UNREADABLE_OUTPUT", FIRST);

		assertThat(this.summaries.attempts(this.alice)).containsExactly(new SummaryAttempt(stored, 1, 5, 1, 0, null, null),
				new SummaryAttempt(discarded, 1, 6, 2, 0, null, null),
				new SummaryAttempt(failed, 1, 7, 3, 1, "UNREADABLE_OUTPUT", FIRST));
	}

	@Test
	void summariesHoldingARemovedArticleAreDeletedAcrossUsersAndOthersStay() {
		long holding = this.userStorylines.create(this.alice, FIRST);
		long alsoHolding = this.userStorylines.create(this.bob, FIRST);
		long clean = this.userStorylines.create(this.alice, FIRST);
		this.summaries.create(summary(holding, "가", FIRST, Map.of(101L, 1001L, 102L, 1002L)));
		this.summaries.create(summary(alsoHolding, "나", FIRST, Map.of(103L, 1002L)));
		this.summaries.create(summary(clean, "다", FIRST, Map.of(104L, 1004L)));
		this.summaries.recordStored(holding, 10, 2);

		assertThat(this.summaries.deleteHolding(List.of(1002L, 9999L))).containsExactly(holding, alsoHolding);

		assertThat(this.summaries.load(this.alice)).extracting(StorylineSummary::userStorylineId).containsExactly(clean);
		assertThat(this.summaries.load(this.bob)).isEmpty();
		// 정리가 지워져도 시도 기록은 남는다
		assertThat(this.summaries.attempts(this.alice))
			.containsExactly(new SummaryAttempt(holding, 1, 10, 2, 0, null, null));
		assertThat(this.summaries.deleteHolding(List.of())).isEmpty();
	}

	@Test
	void readsAreScopedToTheUserAndOrderedByTheLastSummaryTime() {
		long older = this.userStorylines.create(this.alice, FIRST);
		long newer = this.userStorylines.create(this.alice, FIRST);
		long sameTime = this.userStorylines.create(this.alice, FIRST);
		long bobs = this.userStorylines.create(this.bob, FIRST);
		this.summaries.create(summary(older, "앞", FIRST));
		this.summaries.create(summary(sameTime, "같은 때", LATER));
		this.summaries.create(summary(newer, "뒤", LATER));
		this.summaries.create(summary(bobs, "밥", LATER));
		this.summaries.recordStored(bobs, 1, 1);

		assertThat(this.summaries.load(this.alice)).extracting(StorylineSummary::userStorylineId)
			.containsExactly(newer, sameTime, older);
		assertThat(this.summaries.find(this.alice, bobs)).isEmpty();
		assertThat(this.summaries.find(this.bob, bobs)).isPresent();
		assertThat(this.summaries.attempts(this.alice)).isEmpty();
	}

	private static StorylineSummary summary(long id, String title, Instant at) {
		return summary(id, title, at, Map.of(101L, 1001L));
	}

	private static StorylineSummary summary(long id, String title, Instant at, Map<Long, Long> events) {
		return new StorylineSummary(id, new SummaryText(title, "요약", "최근"),
				Map.of(11L, new SummaryText(title, "요약", "최근")), events, "ANTHROPIC", "model", 10, 2, at, at, 0);
	}

	private int count(String table) {
		return this.jdbc.sql("select count(*) from " + table).query(Integer.class).single();
	}

}
