package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 요청의 클라이언트 주소 — proxy 가 덮어쓴 {@code X-Real-IP} 가 IP 리터럴이면 그것, 없거나 읽지 못하면 직접 연결한 주소다.
 * <p>
 * 지키는 규칙: user-R37
 */
class ClientAddressesTests {

	@Test
	void theProxyHeaderWinsOverTheDirectAddress() {
		MockHttpServletRequest request = request("10.0.0.5");
		request.addHeader("X-Real-IP", "192.0.2.44");

		assertThat(ClientAddresses.of(request).limitSubject()).isEqualTo("192.0.2.44");
	}

	@Test
	void withoutTheHeaderTheDirectAddressCounts() {
		assertThat(ClientAddresses.of(request("10.0.0.5")).limitSubject()).isEqualTo("10.0.0.5");
	}

	@Test
	void anUnreadableHeaderFallsBackToTheDirectAddress() {
		MockHttpServletRequest list = request("10.0.0.5");
		list.addHeader("X-Real-IP", "192.0.2.44, 198.51.100.7");
		MockHttpServletRequest name = request("10.0.0.6");
		name.addHeader("X-Real-IP", "example.com");

		assertThat(ClientAddresses.of(list).limitSubject()).isEqualTo("10.0.0.5");
		assertThat(ClientAddresses.of(name).limitSubject()).isEqualTo("10.0.0.6");
	}

	@Test
	void aDirectAddressThatIsNotALiteralIsAnError() {
		assertThatIllegalStateException().isThrownBy(() -> ClientAddresses.of(request("not-an-address")));
	}

	private static MockHttpServletRequest request(String remoteAddress) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/session");
		request.setRemoteAddr(remoteAddress);
		return request;
	}

}
