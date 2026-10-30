package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * CIDR 읽기와 포함 판정.
 * <p>
 * 지키는 규칙: source-R34
 */
class NetworkRangeTests {

	@Test
	void parsesIpv4Range() {
		NetworkRange range = NetworkRange.parse("10.0.0.0/8");

		assertThat(range.prefixLength()).isEqualTo(8);
		assertThat(range.toString()).isEqualTo("10.0.0.0/8");
		assertThat(range.contains(InetAddress.ofLiteral("10.255.255.255"))).isTrue();
		assertThat(range.contains(InetAddress.ofLiteral("11.0.0.0"))).isFalse();
		assertThat(range.contains(InetAddress.ofLiteral("9.255.255.255"))).isFalse();
	}

	@Test
	void parsesIpv6Range() {
		NetworkRange range = NetworkRange.parse("fd00::/8");

		assertThat(range.contains(InetAddress.ofLiteral("fdff::1"))).isTrue();
		assertThat(range.contains(InetAddress.ofLiteral("fe00::1"))).isFalse();
	}

	@Test
	void singleAddressIsAFullLengthRange() {
		assertThat(NetworkRange.parse("192.168.1.10").prefixLength()).isEqualTo(32);
		assertThat(NetworkRange.parse("::1").prefixLength()).isEqualTo(128);
		assertThat(NetworkRange.parse("192.168.1.10").contains(InetAddress.ofLiteral("192.168.1.11"))).isFalse();
	}

	@Test
	void otherFamilyIsNotContained() {
		assertThat(NetworkRange.parse("0.0.0.0/0").contains(InetAddress.ofLiteral("::1"))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "10.0.0.0/33", "10.0.0.1/8", "abc", "example.org/24", "10.0.0.0/", "10.0.0.0/x",
			"10.0.0.0/-1", "::/129", "" })
	void rejectsMalformedRanges(String cidr) {
		assertThatIllegalArgumentException().isThrownBy(() -> NetworkRange.parse(cidr));
	}

	@Test
	void copiesTheAddressArray() {
		byte[] bytes = { 10, 0, 0, 0 };
		NetworkRange range = new NetworkRange(bytes, 8);
		bytes[0] = 11;
		range.address()[0] = 12;

		assertThat(range).isEqualTo(NetworkRange.parse("10.0.0.0/8"));
	}

}
