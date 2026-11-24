package net.yozmnews.account.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 운영자 경로의 인가 판정 — 운영자 토큰 인증, 세션 사용자, 강제 변경, 역할의 순서. LLM 키는 판정의 입력이 아니다.
 * <p>
 * 지키는 규칙: user-R1
 */
class AdminAccessTests {

	@Test
	void operatorTokenOpensWhateverTheSession() {
		assertThat(AdminAccess.decide(true, null, false)).isEqualTo(AdminAccess.OPEN);
		assertThat(AdminAccess.decide(true, true, false)).isEqualTo(AdminAccess.OPEN);
		assertThat(AdminAccess.decide(true, false, false)).isEqualTo(AdminAccess.OPEN);
		assertThat(AdminAccess.decide(true, null, true)).isEqualTo(AdminAccess.OPEN);
		assertThat(AdminAccess.decide(true, true, true)).isEqualTo(AdminAccess.OPEN);
		assertThat(AdminAccess.decide(true, false, true)).isEqualTo(AdminAccess.OPEN);
	}

	@Test
	void withoutTheTokenItNeedsASessionUser() {
		assertThat(AdminAccess.decide(false, null, false)).isEqualTo(AdminAccess.UNAUTHENTICATED);
		assertThat(AdminAccess.decide(false, null, true)).isEqualTo(AdminAccess.UNAUTHENTICATED);
	}

	@Test
	void aUserWhoMustChangeThePasswordIsStoppedBeforeTheRole() {
		assertThat(AdminAccess.decide(false, true, true)).isEqualTo(AdminAccess.PASSWORD_CHANGE_REQUIRED);
		assertThat(AdminAccess.decide(false, true, false)).isEqualTo(AdminAccess.PASSWORD_CHANGE_REQUIRED);
	}

	@Test
	void onlyAnAdminSessionUserOpens() {
		assertThat(AdminAccess.decide(false, false, true)).isEqualTo(AdminAccess.OPEN);
		assertThat(AdminAccess.decide(false, false, false)).isEqualTo(AdminAccess.FORBIDDEN);
	}

}
