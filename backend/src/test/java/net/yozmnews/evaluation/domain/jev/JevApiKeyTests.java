package net.yozmnews.evaluation.domain.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Jev API 키 — 헤더에 실을 수 있는 값만 받고, 값을 문구에 담지 않는다.
 * <p>
 * 지키는 규칙: docs/security.md(로그 · 예외 메시지에 키가 남지 않는다)
 */
class JevApiKeyTests {

	@Test
	void writesTheAuthorizationHeader() {
		assertThat(JevApiKey.of("tsk-Ab1_x.Y~z/9+=").authorization()).isEqualTo("Bearer tsk-Ab1_x.Y~z/9+=");
	}

	@Test
	void hidesTheValueInToString() {
		assertThat(JevApiKey.of("tsk-hidden-value")).hasToString("JevApiKey[숨김]");
	}

	@Test
	void rejectsAnEmptyKey() {
		assertThatIllegalArgumentException().isThrownBy(() -> JevApiKey.of(""))
			.withMessage("Jev API 키는 1자 이상 512자 이하여야 한다 (지금 0자)");
	}

	@Test
	void rejectsALongKeyWithoutItsValue() {
		assertThatIllegalArgumentException().isThrownBy(() -> JevApiKey.of("k".repeat(513)))
			.withMessage("Jev API 키는 1자 이상 512자 이하여야 한다 (지금 513자)");
	}

	@Test
	void acceptsTheLongestKey() {
		assertThat(JevApiKey.of("k".repeat(512)).authorization()).hasSize(519);
	}

	@Test
	void rejectsASpaceWithoutTheValue() {
		assertThatIllegalArgumentException().isThrownBy(() -> JevApiKey.of("tsk-secret part"))
			.withMessage("Jev API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
	}

	@Test
	void rejectsAControlCharacterAndANonAsciiCharacter() {
		assertThatIllegalArgumentException().isThrownBy(() -> JevApiKey.of("tsk-secret\n"))
			.withMessage("Jev API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
		assertThatIllegalArgumentException().isThrownBy(() -> JevApiKey.of("tsk-비밀"))
			.withMessage("Jev API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
	}

}
