package net.yozmnews.user.adapter.out.fake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import net.yozmnews.user.domain.summary.SummaryEvent;
import net.yozmnews.user.domain.summary.SummaryInput;
import net.yozmnews.user.domain.summary.SummaryOutcome;
import net.yozmnews.user.domain.summary.SummaryOutputReader;
import net.yozmnews.user.domain.summary.SummaryPrompt;
import net.yozmnews.user.domain.summary.SummaryPrompts;
import net.yozmnews.user.domain.summary.SummaryRead;
import net.yozmnews.user.domain.summary.SummaryRequest;
import net.yozmnews.user.domain.summary.SummaryText;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 정리 생성기의 로컬 대체물 — 재료에서 고정 규칙으로 만든 출력이 정리 출력의 서술대로 읽힌다. 제목은 그 범위의 가장 늦은 사건의 제목, 흐름 요약은
 * 사건 수를 담은 문장이다. 새 사건이 없는 꼭대기는 지난 글을 그대로 쓴다. profile {@code local} 에서만 등록된다.
 */
class FakeStorylineSummarizerTests {

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 200_000, true);

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final Instant DAY1 = Instant.parse("2026-11-20T00:00:00Z");

	private static final Instant DAY2 = Instant.parse("2026-11-21T00:00:00Z");

	private final FakeStorylineSummarizer summarizer = new FakeStorylineSummarizer();

	@Test
	void aSingleTopIsSummarizedFromItsLatestEvent() {
		SummaryInput input = SummaryInput.fresh(Map.of(10L,
				List.of(new SummaryEvent(101, 1001, DAY1, "앞 사건", "요약"), new SummaryEvent(102, 1002, DAY2, "뒤 사건", null))),
				SEOUL);

		assertThat(read(input)).isEqualTo(new SummaryRead.Read(
				new SummaryText("[로컬 대체물] 뒤 사건", "사건 2건을 정리했다.", "가장 최근 사건: 뒤 사건"),
				List.of(new SummaryText("[로컬 대체물] 뒤 사건", "사건 2건을 정리했다.", "가장 최근 사건: 뒤 사건"))));
	}

	@Test
	void severalTopsGetTheirOwnTextsAndTheWholeCountsAnEventOnce() {
		SummaryEvent shared = new SummaryEvent(101, 1001, DAY1, "함께 든 사건", null);
		SummaryInput input = SummaryInput.fresh(
				Map.of(10L, List.of(shared), 20L, List.of(shared, new SummaryEvent(201, 2001, DAY2, "나 사건", null))),
				SEOUL);

		assertThat(read(input)).isEqualTo(new SummaryRead.Read(
				new SummaryText("[로컬 대체물] 나 사건", "사건 2건을 정리했다.", "가장 최근 사건: 나 사건"),
				List.of(new SummaryText("[로컬 대체물] 함께 든 사건", "사건 1건을 정리했다.", "가장 최근 사건: 함께 든 사건"),
						new SummaryText("[로컬 대체물] 나 사건", "사건 2건을 정리했다.", "가장 최근 사건: 나 사건"))));
	}

	@Test
	void anExtendingCallKeepsThePreviousTextOfATopWithoutNewEvents() {
		SummaryInput input = new SummaryInput(
				Map.of(10L, List.of(), 20L, List.of(new SummaryEvent(202, 2002, DAY2, "새 사건", null))),
				new SummaryText("전체 제목", "전체 요약", "전체 최근"),
				Map.of(10L, new SummaryText("가 제목", "가 요약", "가 최근"), 20L, new SummaryText("나 제목", "나 요약", "나 최근")), SEOUL);

		assertThat(read(input)).isEqualTo(new SummaryRead.Read(
				new SummaryText("[로컬 대체물] 새 사건", "지난 정리에 새 사건 1건을 더했다.", "가장 최근 사건: 새 사건"),
				List.of(new SummaryText("가 제목", "가 요약", "가 최근"),
						new SummaryText("[로컬 대체물] 새 사건", "지난 정리에 새 사건 1건을 더했다.", "가장 최근 사건: 새 사건"))));
	}

	@Test
	void aLongTitleIsCutToTheLimit() {
		SummaryInput input = SummaryInput.fresh(
				Map.of(10L, List.of(new SummaryEvent(101, 1001, DAY1, "가".repeat(500), null))), SEOUL);

		SummaryRead.Read read = (SummaryRead.Read) read(input);

		assertThat(read.whole().title()).isEqualTo("[로컬 대체물] " + "가".repeat(111));
	}

	@Test
	void theAnswerIsCompleteAndCountsCodePointsAsTokens() {
		SummaryInput input = SummaryInput.fresh(Map.of(10L, List.of(new SummaryEvent(101, 1001, DAY1, "사건", null))), SEOUL);
		SummaryPrompt prompt = SummaryPrompts.prompt(input, MODEL);

		// 사용자 구획 {"previous":null,"flows":[["T1",null,[["2026-11-20","사건",""]]]]} 은 64 코드 포인트, 출력은 82 코드 포인트다
		assertThat(this.summarizer.summarize(new SummaryRequest(new UserId(1), MODEL, input, prompt)))
			.isEqualTo(new SummaryOutcome.Generated(
					"{\"whole\":{\"title\":\"[로컬 대체물] 사건\",\"summary\":\"사건 1건을 정리했다.\",\"recent\":\"가장 최근 사건: 사건\"}}",
					64, 82, GenerationStop.COMPLETE));
	}

	@Test
	void theSubstituteIsRegisteredOnlyInLocalDevelopment() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(FakeStorylineSummarizerConfiguration.class);

		runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=local,web,worker")
			.run(context -> assertThat(context.getBean(StorylineSummarizer.class))
				.isInstanceOf(FakeStorylineSummarizer.class));
		runner.withPropertyValues("spring.profiles.default=web,worker", "spring.profiles.active=web,worker")
			.run(context -> assertThat(context).doesNotHaveBean(StorylineSummarizer.class));
	}

	private SummaryRead read(SummaryInput input) {
		SummaryPrompt prompt = SummaryPrompts.prompt(input, MODEL);
		SummaryOutcome.Generated generated = (SummaryOutcome.Generated) this.summarizer
			.summarize(new SummaryRequest(new UserId(1), MODEL, input, prompt));
		return SummaryOutputReader.read(generated.text(), prompt.aliases());
	}

}
