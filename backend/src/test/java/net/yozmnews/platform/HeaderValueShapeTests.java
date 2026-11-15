package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import net.yozmnews.platform.HeaderValueShape.Violation;
import org.junit.jupiter.api.Test;

/**
 * 요청 헤더에 실을 값의 꼴 — 1 ~ 512자, 출력 가능한 ASCII(U+0021 ~ U+007E)뿐. 길이를 먼저 본다.
 * <p>
 * 지키는 규칙: platform-R23
 */
class HeaderValueShapeTests {

	@Test
	void theLongestValueIs512Characters() {
		assertThat(HeaderValueShape.MAX_LENGTH).isEqualTo(512);
		assertThat(HeaderValueShape.violation("k")).isEmpty();
		assertThat(HeaderValueShape.violation("k".repeat(512))).isEmpty();
		assertThat(HeaderValueShape.violation("k".repeat(513))).contains(Violation.LENGTH);
	}

	@Test
	void anEmptyValueViolatesTheLength() {
		assertThat(HeaderValueShape.violation("")).contains(Violation.LENGTH);
	}

	@Test
	void acceptsEveryPrintableAsciiCharacter() {
		assertThat(HeaderValueShape.violation("!")).isEmpty();
		assertThat(HeaderValueShape.violation("~")).isEmpty();
		assertThat(HeaderValueShape.violation("sk-Ab1_x.Y~z/9+=")).isEmpty();
	}

	@Test
	void rejectsSpacesControlCharactersAndNonAscii() {
		assertThat(HeaderValueShape.violation("sk secret")).contains(Violation.CHARACTER);
		assertThat(HeaderValueShape.violation("sk-secret\u007F")).contains(Violation.CHARACTER);
		assertThat(HeaderValueShape.violation("sk-secret\r\nX-Injected: 1")).contains(Violation.CHARACTER);
		assertThat(HeaderValueShape.violation("sk-secret\t")).contains(Violation.CHARACTER);
		assertThat(HeaderValueShape.violation("sk-비밀")).contains(Violation.CHARACTER);
	}

	@Test
	void looksAtTheLengthFirst() {
		assertThat(HeaderValueShape.violation(" ".repeat(513))).contains(Violation.LENGTH);
	}

	@Test
	void rejectsNull() {
		assertThatNullPointerException().isThrownBy(() -> HeaderValueShape.violation(null));
	}

}
