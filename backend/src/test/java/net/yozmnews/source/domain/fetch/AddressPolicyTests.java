package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 차단 대역 표의 행마다 대표 주소 하나, 그 경계 밖 공개 주소, 허용 목록.
 */
class AddressPolicyTests {

	private static final AddressPolicy DEFAULT = new AddressPolicy(List.of());

	@ParameterizedTest
	@ValueSource(strings = { "0.0.0.1", "10.1.2.3", "172.31.0.1", "192.168.0.1", "100.64.0.1", "127.0.0.1",
			"169.254.169.254", "192.0.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1", "198.18.0.1", "224.0.0.1",
			"240.0.0.1", "255.255.255.255", "::", "::1", "100::1", "2001::1", "2001:db8::1", "fc00::1", "fe80::1",
			"fec0::1", "ff02::1", "::10.0.0.1", "64:ff9b::a00:1", "2002:a00:1::", "::ffff:10.0.0.1",
			"::ffff:127.0.0.1" })
	void blocksInternalAddresses(String literal) {
		assertThat(DEFAULT.permits(address(literal))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "93.184.215.14", "2606:4700::1111", "172.32.0.1", "100.128.0.1", "2002:5db8:d70e::",
			"64:ff9b::5db8:d70e", "11.0.0.1", "8.8.8.8" })
	void permitsPublicAddresses(String literal) {
		assertThat(DEFAULT.permits(address(literal))).isTrue();
	}

	@Test
	void allowedRangeOpensOnlyItself() {
		AddressPolicy policy = new AddressPolicy(List.of(NetworkRange.parse("10.0.0.0/8")));

		assertThat(policy.permits(address("10.1.2.3"))).isTrue();
		assertThat(policy.permits(address("11.0.0.1"))).isTrue();
		assertThat(policy.permits(address("127.0.0.1"))).isFalse();
		assertThat(policy.permits(address("192.168.0.1"))).isFalse();
	}

	@Test
	void mixedResultIsRejectedAtItsFirstBlockedAddress() {
		assertThat(DEFAULT.firstBlocked(List.of(address("93.184.215.14"), address("10.0.0.1"))))
			.contains(address("10.0.0.1"));
	}

	@Test
	void allPublicResultIsAccepted() {
		assertThat(DEFAULT.firstBlocked(List.of(address("93.184.215.14"), address("2606:4700::1111")))).isEmpty();
	}

	@Test
	void blockedTableHasTwentyThreeRanges() {
		assertThat(AddressPolicy.BLOCKED).hasSize(23);
	}

	private static InetAddress address(String literal) {
		return InetAddress.ofLiteral(literal);
	}

}
