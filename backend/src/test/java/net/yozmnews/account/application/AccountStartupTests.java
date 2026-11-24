package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 계정 쪽 기동 단계 — 첫 운영자가 도는 단계는 웹 서버 시작 단계보다 작아 웹 서버가 요청을 받기 전에 끝난다.
 * <p>
 * 지키는 규칙: account-R27
 */
class AccountStartupTests {

	/** Spring Boot 4.1 의 서블릿 웹 서버 시작 단계({@code Integer.MAX_VALUE - 2048}) */
	private static final int WEB_SERVER_PHASE = 2147481599;

	@Test
	void theFirstAdminPhaseComesBeforeTheWebServer() {
		assertThat(AccountStartup.PHASE).isLessThan(WEB_SERVER_PHASE);
	}

}
