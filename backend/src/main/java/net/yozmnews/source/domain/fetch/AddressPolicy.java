package net.yozmnews.source.domain.fetch;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 내부 주소 판정 — docs/specs/source.md §4 · source-R33 · source-R34
 */
public final class AddressPolicy {

	/** 차단 대역 — 정본 */
	public static final List<NetworkRange> BLOCKED = List.of(
			// IPv4
			NetworkRange.parse("0.0.0.0/8"), // "이 망" — 0.0.0.0 은 많은 OS 에서 로컬로 간다
			NetworkRange.parse("10.0.0.0/8"), NetworkRange.parse("172.16.0.0/12"),
			NetworkRange.parse("192.168.0.0/16"), // 사설(RFC 1918)
			NetworkRange.parse("100.64.0.0/10"), // 공유 주소(CGNAT, RFC 6598)
			NetworkRange.parse("127.0.0.0/8"), // 루프백
			NetworkRange.parse("169.254.0.0/16"), // 링크 로컬 — 클라우드 메타데이터 169.254.169.254
			NetworkRange.parse("192.0.0.0/24"), NetworkRange.parse("192.0.2.0/24"),
			NetworkRange.parse("198.51.100.0/24"), NetworkRange.parse("203.0.113.0/24"), // IETF 프로토콜 할당 · 문서용
			NetworkRange.parse("198.18.0.0/15"), // 벤치마크
			NetworkRange.parse("224.0.0.0/4"), NetworkRange.parse("240.0.0.0/4"), // 멀티캐스트 · 예약(255.255.255.255 포함)
			// IPv6
			NetworkRange.parse("::/128"), NetworkRange.parse("::1/128"), // 미지정 · 루프백
			NetworkRange.parse("100::/64"), // 폐기 접두(RFC 6666)
			NetworkRange.parse("2001::/32"), NetworkRange.parse("2001:db8::/32"), // Teredo · 문서용
			NetworkRange.parse("fc00::/7"), NetworkRange.parse("fe80::/10"), NetworkRange.parse("fec0::/10"),
			NetworkRange.parse("ff00::/8")); // 고유 로컬 · 링크 로컬 · 폐기된 사이트 로컬 · 멀티캐스트

	/** IPv4 호환(::/96) — 마지막 4바이트가 IPv4 */
	private static final NetworkRange IPV4_COMPATIBLE = NetworkRange.parse("::/96");

	/** NAT64(64:ff9b::/96) — 마지막 4바이트가 IPv4 */
	private static final NetworkRange NAT64 = NetworkRange.parse("64:ff9b::/96");

	/** 6to4(2002::/16) — 3~6번째 바이트가 IPv4 */
	private static final NetworkRange SIX_TO_FOUR = NetworkRange.parse("2002::/16");

	private final List<NetworkRange> allowed;

	/**
	 * 허용 대역으로 정책을 만든다.
	 * @param allowed 운영 설정의 허용 대역 — 비어 있을 수 있다
	 */
	public AddressPolicy(List<NetworkRange> allowed) {
		this.allowed = List.copyOf(allowed);
	}

	/**
	 * 해석 결과 가운데 처음으로 막힌 주소를 찾는다 — source-R33.
	 * @return 없으면 비어 있다(전부 허용)
	 */
	public Optional<InetAddress> firstBlocked(List<InetAddress> addresses) {
		return addresses.stream().filter(address -> !permits(address)).findFirst();
	}

	/** 주소를 판정한다 — 허용 목록에 들면 허용, 아니면 차단 대역(IPv4 를 품은 IPv6 는 품은 IPv4 도)에 들면 차단 */
	boolean permits(InetAddress address) {
		Objects.requireNonNull(address, "address");
		if (this.allowed.stream().anyMatch(range -> range.contains(address))) {
			return true;
		}
		return !isBlocked(address.getAddress());
	}

	private static boolean isBlocked(byte[] bytes) {
		if (BLOCKED.stream().anyMatch(range -> range.containsBytes(bytes))) {
			return true;
		}
		return embeddedIpv4(bytes).map(AddressPolicy::isBlocked).orElse(false);
	}

	/** IPv4 를 품은 IPv6 형식이면 품은 IPv4 를 꺼낸다 — IPv4 대응(::ffff:0:0/96)은 JDK 가 {@code Inet4Address} 로 바꾸므로 여기 오지 않는다 */
	private static Optional<byte[]> embeddedIpv4(byte[] bytes) {
		if (bytes.length != 16) {
			return Optional.empty();
		}
		if (IPV4_COMPATIBLE.containsBytes(bytes) || NAT64.containsBytes(bytes)) {
			return Optional.of(Arrays.copyOfRange(bytes, 12, 16));
		}
		if (SIX_TO_FOUR.containsBytes(bytes)) {
			return Optional.of(Arrays.copyOfRange(bytes, 2, 6));
		}
		return Optional.empty();
	}

}
