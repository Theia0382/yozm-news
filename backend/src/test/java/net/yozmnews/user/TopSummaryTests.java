package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 꼭대기 하나의 글 — 세 칸은 함께 있거나 함께 없다.
 */
class TopSummaryTests {

	private static final StorylineId TOP = new StorylineId(11);

	@Test
	void aTextWithOnlySomeOfItsFieldsIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new TopSummary(TOP, "제목", null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new TopSummary(TOP, null, "요약", null));
		assertThatIllegalArgumentException().isThrownBy(() -> new TopSummary(TOP, null, null, "최근"));
		assertThatIllegalArgumentException().isThrownBy(() -> new TopSummary(TOP, "제목", "요약", null));
	}

	@Test
	void aTopIsWrittenOnlyWhenItHasItsText() {
		// 대조 — 세 칸이 다 있는 글과 글 없는 꼭대기
		assertThat(new TopSummary(TOP, "제목", "요약", "최근").written()).isTrue();
		assertThat(TopSummary.without(TOP)).isEqualTo(new TopSummary(TOP, null, null, null));
		assertThat(TopSummary.without(TOP).written()).isFalse();
	}

}
