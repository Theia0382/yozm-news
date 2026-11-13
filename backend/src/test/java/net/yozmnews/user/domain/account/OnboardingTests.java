package net.yozmnews.user.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 계정을 만드는 길 — 길마다 역할 · 강제 변경 · 만들 때 복구 코드.
 * <p>
 * 지키는 규칙: user-R28
 */
class OnboardingTests {

	@Test
	void onlyTheFirstAdminIsAnAdmin() {
		assertThat(Onboarding.OPEN_SIGN_UP.role()).isEqualTo(AccountRole.USER);
		assertThat(Onboarding.INVITATION.role()).isEqualTo(AccountRole.USER);
		assertThat(Onboarding.OPERATOR.role()).isEqualTo(AccountRole.USER);
		assertThat(Onboarding.FIRST_ADMIN.role()).isEqualTo(AccountRole.ADMIN);
	}

	@Test
	void initialPasswordsChosenBySomeoneElseMustBeChangedAndGetTheRecoveryCodeLater() {
		assertThat(Onboarding.OPEN_SIGN_UP.mustChangePassword()).isFalse();
		assertThat(Onboarding.INVITATION.mustChangePassword()).isFalse();
		assertThat(Onboarding.OPERATOR.mustChangePassword()).isTrue();
		assertThat(Onboarding.FIRST_ADMIN.mustChangePassword()).isTrue();

		assertThat(Onboarding.OPEN_SIGN_UP.recoveryCodeAtCreation()).isTrue();
		assertThat(Onboarding.INVITATION.recoveryCodeAtCreation()).isTrue();
		assertThat(Onboarding.OPERATOR.recoveryCodeAtCreation()).isFalse();
		assertThat(Onboarding.FIRST_ADMIN.recoveryCodeAtCreation()).isFalse();
	}

}
