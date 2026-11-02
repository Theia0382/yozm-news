package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 평가 입력 — 제목 · 요약 · 피드 맥락 목록의 값 검사
 */
class EvaluationInputTests {

	@Test
	void refusesAMissingOrBlankTitle() {
		assertThatNullPointerException().isThrownBy(() -> new EvaluationInput(null, null, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationInput(" \t", null, List.of()));
	}

	@Test
	void acceptsATitleWithoutSummaryOrFeeds() {
		EvaluationInput input = new EvaluationInput("국회 본회의 열려", null, List.of());

		assertThat(input.title()).isEqualTo("국회 본회의 열려");
		assertThat(input.summary()).isNull();
		assertThat(input.feeds()).isEmpty();
	}

	@Test
	void refusesABlankSummary() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationInput("제목", "  ", List.of()));
		assertThat(new EvaluationInput("제목", "요약", List.of()).summary()).isEqualTo("요약");
	}

	@Test
	void refusesAMissingFeed() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EvaluationInput("제목", null, Arrays.asList(new FeedContext(null, null, List.of()), null)));
	}

	@Test
	void copiesTheFeeds() {
		List<FeedContext> feeds = new ArrayList<>(List.of(new FeedContext("동아일보", "정치", List.of())));
		EvaluationInput input = new EvaluationInput("제목", null, feeds);

		feeds.add(new FeedContext("경향신문", "사회", List.of()));

		assertThat(input.feeds()).containsExactly(new FeedContext("동아일보", "정치", List.of()));
	}

}
