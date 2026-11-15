package net.yozmnews.user.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 클라이언트 주소 — IP 리터럴만 읽고(이름 조회 없음), IPv6 은 앞 64비트로 묶어 세며, IPv4 사상 주소는 IPv4 로 본다.
 * <p>
 * 지키는 규칙: user-R37
 */
class ClientAddressTests {

	@Test
	void ipv4IsCountedByTheWholeAddress() {
		assertThat(ClientAddress.parse("192.0.2.1").orElseThrow().limitSubject()).isEqualTo("192.0.2.1");
	}

	@Test
	void ipv6AddressesInTheSameSlash64AreCountedTogether() {
		String first = ClientAddress.parse("2001:db8:1:2:3:4:5:6").orElseThrow().limitSubject();
		String second = ClientAddress.parse("2001:db8:1:2:ffff::1").orElseThrow().limitSubject();

		assertThat(first).isEqualTo("20010db800010002/64").isEqualTo(second);
	}

	@Test
	void differentSlash64sAreCountedApart() {
		assertThat(ClientAddress.parse("2001:db8:1:3::1").orElseThrow().limitSubject())
			.isEqualTo("20010db800010003/64");
	}

	@Test
	void ipv4MappedIpv6IsCountedAsIpv4() {
		assertThat(ClientAddress.parse("::ffff:192.0.2.1").orElseThrow().limitSubject()).isEqualTo("192.0.2.1");
	}

	@Test
	void scopedIpv6IsReadAndItsLiteralDropsTheScope() {
		ClientAddress scoped = ClientAddress.parse("fe80::1%1").orElseThrow();

		assertThat(scoped.limitSubject()).isEqualTo("fe80000000000000/64");
		assertThat(scoped.literal()).isEqualTo("fe80:0:0:0:0:0:0:1");
	}

	@Test
	void aScopeNamedByInterfaceIsNotALiteral() {
		assertThat(ClientAddress.parse("fe80::1%eth0")).isEmpty();
	}

	@Test
	void namesBlanksAndListsAreNotAddresses() {
		assertThat(ClientAddress.parse("example.com")).isEmpty();
		assertThat(ClientAddress.parse("localhost")).isEmpty();
		assertThat(ClientAddress.parse("")).isEmpty();
		assertThat(ClientAddress.parse(null)).isEmpty();
		assertThat(ClientAddress.parse(" 192.0.2.1")).isEmpty();
		assertThat(ClientAddress.parse("192.0.2.1, 198.51.100.7")).isEmpty();
	}

	@Test
	void bracketedIpv6IsStillALiteral() {
		assertThat(ClientAddress.parse("[2001:db8::1]").orElseThrow().limitSubject())
			.isEqualTo("20010db800000000/64");
	}

}
