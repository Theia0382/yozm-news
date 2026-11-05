package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 코드 포인트 단위 자르기 — 상한 이하면 그대로, 넘으면 앞 상한만큼, 서로게이트 쌍을 가르지 않는다, 음수 상한 거부.
 */
class CodePointsTests {

	/** 한자 확장 B 의 첫 글자 U+20000 — 코드 포인트 하나가 UTF-16 두 단위(서로게이트 쌍)다 */
	private static final String PAIR = "𠀀";

	@Test
	void aTextWithinTheLimitIsReturnedAsItIs() {
		String text = "가나다";

		assertThat(CodePoints.prefix(text, 3)).isSameAs(text);
		assertThat(CodePoints.prefix(text, 4)).isSameAs(text);
	}

	@Test
	void aLongerTextIsCutToTheLimit() {
		assertThat(CodePoints.prefix("가나다", 2)).isEqualTo("가나");
		assertThat(CodePoints.prefix("가나다", 0)).isEmpty();
	}

	@Test
	void theLimitCountsCodePointsNotCodeUnits() {
		assertThat(CodePoints.prefix(PAIR.repeat(3), 2)).isEqualTo(PAIR + PAIR).hasSize(4);
		assertThat(CodePoints.prefix("가" + PAIR + "나", 2)).isEqualTo("가" + PAIR);
	}

	@Test
	void aNegativeLimitIsRejected() {
		assertThatThrownBy(() -> CodePoints.prefix("가", -1)).isInstanceOf(IllegalArgumentException.class);
	}

}
