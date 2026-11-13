package net.yozmnews.user.domain.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.user.domain.account.EmailAddress;
import org.junit.jupiter.api.Test;

/**
 * 첫 운영자 설정 값 — 빈 값은 없는 것, 하나만 · 규칙 위반은 그 변수 이름을 말하고, 비밀번호는 정규화하며 문자열에 싣지 않는다.
 * <p>
 * 지키는 규칙: user-R34
 */
class FirstAdminValuesTests {

	@Test
	void blankValuesAreAbsent() {
		assertThat(FirstAdminValues.of(null, null)).isEqualTo(new FirstAdminValues.None());
		assertThat(FirstAdminValues.of("  ", "")).isEqualTo(new FirstAdminValues.None());
	}

	@Test
	void oneWithoutTheOtherNamesTheMissingVariable() {
		assertThat(FirstAdminValues.of("ops@example.org", null)).isEqualTo(
				new FirstAdminValues.Incomplete("yozmnews.first-admin.password(YOZMNEWS_FIRST_ADMIN_PASSWORD)"));
		assertThat(FirstAdminValues.of("", "correct horse"))
			.isEqualTo(new FirstAdminValues.Incomplete("yozmnews.first-admin.email(YOZMNEWS_FIRST_ADMIN_EMAIL)"));
	}

	@Test
	void malformedValuesSayWhichVariableWithoutTheValue() {
		assertThat(FirstAdminValues.of("not an email", "correct horse")).isEqualTo(
				new FirstAdminValues.Invalid("yozmnews.first-admin.email(YOZMNEWS_FIRST_ADMIN_EMAIL) 이 이메일이 아니다"));
		FirstAdminValues shortPassword = FirstAdminValues.of("ops@example.org", "short!!");
		assertThat(shortPassword).isEqualTo(new FirstAdminValues.Invalid(
				"yozmnews.first-admin.password(YOZMNEWS_FIRST_ADMIN_PASSWORD) 이 비밀번호 규칙(8 ~ 128 코드 포인트)을 어긴다"));
		assertThat(shortPassword.toString()).doesNotContain("short!!");
	}

	@Test
	void givenValuesAreNormalizedAndThePasswordStaysHidden() {
		FirstAdminValues given = FirstAdminValues.of(" Admin.Ops@Example.org ", "ﬁﬁﬁﬁ horse");

		assertThat(given).isEqualTo(new FirstAdminValues.Given(EmailAddress.parse("admin.ops@example.org").orElseThrow(),
				"fifififi horse"));
		assertThat(given.toString()).contains("admin.ops@example.org").doesNotContain("horse");
	}

}
