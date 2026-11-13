package net.yozmnews.user.domain.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.user.domain.account.EmailAddress;
import org.junit.jupiter.api.Test;

/**
 * 첫 운영자 판정 — 운영자가 있으면 무엇이 남았든 경고만, 운영자가 없으면 값 없음은 경고 · 하나만 · 규칙 위반 · 이미 있는 이메일은 기동 실패 ·
 * 그 밖은 만든다.
 * <p>
 * 지키는 규칙: user-R34
 */
class FirstAdminDecisionTests {

	private static final FirstAdminValues NONE = new FirstAdminValues.None();

	private static final FirstAdminValues INCOMPLETE = new FirstAdminValues.Incomplete("빠진 변수");

	private static final FirstAdminValues INVALID = new FirstAdminValues.Invalid("규칙 위반");

	private static final EmailAddress EMAIL = EmailAddress.parse("ops@example.org").orElseThrow();

	private static final FirstAdminValues GIVEN = new FirstAdminValues.Given(EMAIL, "correct horse");

	@Test
	void anAdminMakesEveryLeftoverOnlyAWarning() {
		assertThat(FirstAdminDecision.decide(NONE, true, false)).isEqualTo(new FirstAdminDecision.Nothing());
		assertThat(FirstAdminDecision.decide(INCOMPLETE, true, false)).isEqualTo(new FirstAdminDecision.WarnLeftover());
		assertThat(FirstAdminDecision.decide(INVALID, true, false)).isEqualTo(new FirstAdminDecision.WarnLeftover());
		assertThat(FirstAdminDecision.decide(GIVEN, true, false)).isEqualTo(new FirstAdminDecision.WarnLeftover());
		assertThat(FirstAdminDecision.decide(GIVEN, true, true)).isEqualTo(new FirstAdminDecision.WarnLeftover());
	}

	@Test
	void withoutAnAdminTheValuesDecide() {
		assertThat(FirstAdminDecision.decide(NONE, false, false)).isEqualTo(new FirstAdminDecision.WarnNoAdmin());
		assertThat(FirstAdminDecision.decide(INCOMPLETE, false, false)).isEqualTo(
				new FirstAdminDecision.Fail("빠진 변수 이 없다 — 첫 운영자의 이메일 · 초기 비밀번호는 함께 준다"));
		assertThat(FirstAdminDecision.decide(INVALID, false, false)).isEqualTo(new FirstAdminDecision.Fail("규칙 위반"));
		assertThat(FirstAdminDecision.decide(GIVEN, false, true)).isEqualTo(new FirstAdminDecision.Fail(
				"yozmnews.first-admin.email(YOZMNEWS_FIRST_ADMIN_EMAIL) 이 이미 있는 계정의 이메일이다 — 다른 이메일을 준다"));
		assertThat(FirstAdminDecision.decide(GIVEN, false, false))
			.isEqualTo(new FirstAdminDecision.Create(EMAIL, "correct horse"));
	}

}
