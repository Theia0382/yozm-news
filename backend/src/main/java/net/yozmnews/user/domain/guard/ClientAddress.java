package net.yozmnews.user.domain.guard;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 요청을 보낸 클라이언트의 주소 — 요청 제한이 IP 로 세는 대상이다. docs/specs/user.md §4 · user-R37
 * <p>
 * IP 리터럴만 읽고 이름을 조회하지 않는다. IPv6 은 앞 64비트로 묶어 센다 — 한 가입자가 보통 /64 이상을 받아 그 안의 주소를 마음대로 바꿀 수
 * 있다. IPv4 사상 IPv6 주소는 IPv4 로 읽는다.
 *
 * @param address 주소 — IPv4 사상 IPv6 은 이미 IPv4 다
 */
public record ClientAddress(InetAddress address) {

	/** IPv6 에서 세는 단위의 앞 비트 수 */
	static final int IPV6_PREFIX_BITS = 64;

	public ClientAddress {
		Objects.requireNonNull(address, "address");
	}

	/**
	 * IP 리터럴을 읽는다 — 앞뒤 공백을 허용하지 않고, 이름은 조회하지 않는다.
	 * @return 비었거나 IP 리터럴이 아니면 비어 있다
	 */
	public static Optional<ClientAddress> parse(@Nullable String literal) {
		if (literal == null || literal.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ClientAddress(InetAddress.ofLiteral(literal)));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/** 요청 제한이 세는 대상 — IPv4 는 점 표기, IPv6 은 앞 64비트의 16진과 {@code /64} */
	public String limitSubject() {
		if (this.address instanceof Inet4Address v4) {
			return v4.getHostAddress();
		}
		if (this.address instanceof Inet6Address v6) {
			return HexFormat.of().formatHex(v6.getAddress(), 0, IPV6_PREFIX_BITS / Byte.SIZE) + "/" + IPV6_PREFIX_BITS;
		}
		// InetAddress 는 닫힌 타입이 아니다 — 두 갈래 밖의 주소는 JDK 가 만들지 않는다
		throw new IllegalStateException("IPv4 도 IPv6 도 아닌 주소다: " + this.address.getClass().getName());
	}

	/** 주소 그대로의 문자열 — IPv6 의 영역 표시({@code %…})는 떼어 낸다 */
	public String literal() {
		String hostAddress = this.address.getHostAddress();
		int scope = hostAddress.indexOf('%');
		return (scope < 0) ? hostAddress : hostAddress.substring(0, scope);
	}

}
