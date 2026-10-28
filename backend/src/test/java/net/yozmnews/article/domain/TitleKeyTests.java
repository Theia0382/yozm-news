package net.yozmnews.article.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 제목 비교 키 — 공백 · 전각/반각 · 대소문자만 맞추고 말머리 · 구두점은 남긴다.
 */
class TitleKeyTests {

	@Test
	void keepsBracketedLabels() {
		assertThat(TitleKey.of("[속보] 국회 본회의 통과").value()).isEqualTo("[속보]국회본회의통과");
	}

	@Test
	void foldsFullWidthFormsAndIdeographicSpace() {
		assertThat(TitleKey.of("ＡＢＣ\u3000뉴스").value()).isEqualTo("abc뉴스");
	}

	@Test
	void removesTabsAndLowercases() {
		assertThat(TitleKey.of("AI 반도체\t수출").value()).isEqualTo("ai반도체수출");
	}

	@Test
	void removesSeparatorsThatNfkcKeeps() {
		// U+2028(줄 구분) · U+1680(오검 공백)은 NFKC 뒤에도 그대로 남고 \s 에 들지 않는다
		assertThat(TitleKey.of("국회\u2028본회의\u1680통과").value()).isEqualTo("국회본회의통과");
	}

	@Test
	void becomesEmptyForSeparatorOnlyTitles() {
		// String.isBlank 는 U+00A0 을 공백으로 보지 않아 이 제목은 FetchedArticle 을 통과한다
		assertThat("\u00A0\u2003".isBlank()).isFalse();
		TitleKey key = TitleKey.of("\u00A0\u2003");

		assertThat(key.value()).isEmpty();
		assertThat(key.isEmpty()).isTrue();
	}

	@Test
	void hashesTheKey() {
		assertThat(TitleKey.of("\u00A0").hash())
			.hasToString("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
	}

}
