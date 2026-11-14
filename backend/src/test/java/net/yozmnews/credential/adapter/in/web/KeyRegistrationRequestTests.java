package net.yozmnews.credential.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 키 등록 요청의 문자열 표현 — 로그나 예외 문구에 요청이 찍혀도 키가 나오지 않는다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: credential-R19
 */
class KeyRegistrationRequestTests {

	@Test
	void theStringFormHidesTheKey() {
		KeyRegistrationRequest request = new KeyRegistrationRequest("sk-request-0123456789abWXYZ");

		assertThat(request).hasToString("KeyRegistrationRequest[key=숨김]");
		assertThat("요청 " + request).isEqualTo("요청 KeyRegistrationRequest[key=숨김]");
		// 대조 — 값은 그대로 들고 있다
		assertThat(request.key()).isEqualTo("sk-request-0123456789abWXYZ");
	}

}
