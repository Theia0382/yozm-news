package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 원문 위치의 공개 보기 줄 · 열 — 모르는 위치({@link TextLocation#UNKNOWN})만 null 로 옮기는지 수기 리터럴로 본다 */
class TextLocationTests {

	@Test
	void anUnknownLocationHasNoLineOrColumnInViews() {
		assertThat(TextLocation.UNKNOWN.knownLine()).isNull();
		assertThat(TextLocation.UNKNOWN.knownColumn()).isNull();
	}

	@Test
	void aKnownLocationKeepsItsLineAndColumn() {
		TextLocation location = new TextLocation(3, 7);

		assertThat(location.knownLine()).isEqualTo(3);
		assertThat(location.knownColumn()).isEqualTo(7);
	}

	@Test
	void onlyTheUnknownValueIsUnknown() {
		TextLocation location = new TextLocation(0, 5);

		assertThat(location.knownLine()).isEqualTo(0);
		assertThat(location.knownColumn()).isEqualTo(5);
	}

}
