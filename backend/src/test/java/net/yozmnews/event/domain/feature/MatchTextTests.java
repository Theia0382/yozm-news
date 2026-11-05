package net.yozmnews.event.domain.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 표현 텍스트 — 제목 + 요약문 앞 300 코드 포인트, 요약문이 없거나 공백뿐이면 제목만.
 * <p>
 * 지키는 규칙: event-R1
 */
class MatchTextTests {

	private static final String TITLE = "한국은행 기준금리 동결";

	/** U+1D400 — 코드 유닛 둘(서로게이트 쌍)인 코드 포인트 하나 */
	private static final String SURROGATE_PAIR = "𝐀";

	@Test
	void anArticleWithoutASummaryIsItsTitle() {
		assertThat(MatchText.compose(TITLE, null)).isEqualTo("한국은행 기준금리 동결");
	}

	@Test
	void aBlankSummaryIsNoSummary() {
		assertThat(MatchText.compose(TITLE, "   ")).isEqualTo("한국은행 기준금리 동결");
	}

	@Test
	void theSummaryIsStrippedAndJoinedByOneLineBreak() {
		assertThat(MatchText.compose(TITLE, "  리드 문장.  ")).isEqualTo("한국은행 기준금리 동결\n리드 문장.");
	}

	@Test
	void theTitleIsKeptAsItIs() {
		assertThat(MatchText.compose("  제목  ", null)).isEqualTo("  제목  ");
	}

	@Test
	void aSummaryOfExactly300CodePointsIsKeptWhole() {
		assertThat(MatchText.compose(TITLE, "가".repeat(300))).isEqualTo(TITLE + "\n" + "가".repeat(300));
	}

	@Test
	void aSummaryOf301CodePointsIsCutTo300() {
		assertThat(MatchText.compose(TITLE, "가".repeat(301))).isEqualTo(TITLE + "\n" + "가".repeat(300));
	}

	@Test
	void aVeryLongSummaryIsCutTo300() {
		assertThat(MatchText.compose(TITLE, "나".repeat(1000))).isEqualTo(TITLE + "\n" + "나".repeat(300));
	}

	@Test
	void theCutCountsCodePointsAndDoesNotSplitASurrogatePair() {
		String composed = MatchText.compose(TITLE, SURROGATE_PAIR.repeat(301));

		assertThat(composed).isEqualTo(TITLE + "\n" + SURROGATE_PAIR.repeat(300));
		assertThat(composed.substring(TITLE.length() + 1)).hasSize(600);
	}

	@Test
	void aSurrogatePairAtTheBoundaryIsKeptWholeOrDropped() {
		assertThat(MatchText.compose(TITLE, "가".repeat(299) + SURROGATE_PAIR.repeat(2)))
			.isEqualTo(TITLE + "\n" + "가".repeat(299) + SURROGATE_PAIR);
	}

	@Test
	void aBlankTitleIsRejected() {
		assertThatThrownBy(() -> MatchText.compose("  ", "요약")).isInstanceOf(IllegalArgumentException.class);
	}

}
