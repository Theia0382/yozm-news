package net.yozmnews.source.domain.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/**
 * CIDR 대역 하나 — docs/specs/source.md §4 · source-R33 · source-R34.
 * <p>
 * 배열을 생성자와 접근자에서 복사한다 — 밖에서 바꿀 수 없다.
 *
 * @param address 대역의 시작 주소(IPv4 4바이트 · IPv6 16바이트) — 접두 뒤 비트는 0
 * @param prefixLength 접두 길이
 */
public record NetworkRange(byte[] address, int prefixLength) {

	/**
	 * 값을 확인한다.
	 * @throws IllegalArgumentException 주소 길이가 4 · 16 이 아니거나, 접두 길이가 범위 밖이거나, 접두 뒤 비트가 0 이 아닐 때
	 */
	public NetworkRange {
		Objects.requireNonNull(address, "address");
		address = address.clone();
		if (address.length != 4 && address.length != 16) {
			throw new IllegalArgumentException("주소는 4 · 16 바이트다: " + address.length);
		}
		if (prefixLength < 0 || prefixLength > address.length * 8) {
			throw new IllegalArgumentException("접두 길이가 범위 밖이다: " + prefixLength);
		}
		for (int bit = prefixLength; bit < address.length * 8; bit++) {
			if (bitAt(address, bit)) {
				throw new IllegalArgumentException("접두 뒤 비트가 0 이 아니다: /" + prefixLength);
			}
		}
	}

	/**
	 * {@code 10.0.0.0/8} · {@code fd00::/8} · {@code 192.168.1.10}(단일 주소 — /32 · /128) 을 읽는다. 이름은 받지 않고
	 * 해석하지도 않는다.
	 * @throws IllegalArgumentException 형식이 틀렸을 때
	 */
	public static NetworkRange parse(String cidr) {
		Objects.requireNonNull(cidr, "cidr");
		String text = cidr.strip();
		int slash = text.indexOf('/');
		String literal = (slash < 0) ? text : text.substring(0, slash);
		InetAddress base;
		try {
			base = InetAddress.ofLiteral(literal);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("IP 주소 리터럴이 아니다: " + cidr, ex);
		}
		byte[] bytes = base.getAddress();
		if (slash < 0) {
			return new NetworkRange(bytes, bytes.length * 8);
		}
		String prefix = text.substring(slash + 1);
		if (prefix.isEmpty() || !prefix.chars().allMatch(c -> c >= '0' && c <= '9') || prefix.length() > 3) {
			throw new IllegalArgumentException("접두 길이가 숫자가 아니다: " + cidr);
		}
		try {
			return new NetworkRange(bytes, Integer.parseInt(prefix));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("CIDR 대역이 아니다: " + cidr + " — " + ex.getMessage(), ex);
		}
	}

	@Override
	public byte[] address() {
		return this.address.clone();
	}

	/**
	 * 주소가 이 대역에 드는지 본다.
	 * @return 주소 계열이 다르면 false
	 */
	public boolean contains(InetAddress candidate) {
		return containsBytes(candidate.getAddress());
	}

	boolean containsBytes(byte[] candidate) {
		if (candidate.length != this.address.length) {
			return false;
		}
		for (int bit = 0; bit < this.prefixLength; bit++) {
			if (bitAt(candidate, bit) != bitAt(this.address, bit)) {
				return false;
			}
		}
		return true;
	}

	private static boolean bitAt(byte[] bytes, int bit) {
		return (bytes[bit / 8] & (0x80 >>> (bit % 8))) != 0;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NetworkRange range && this.prefixLength == range.prefixLength
				&& Arrays.equals(this.address, range.address);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(this.address) + this.prefixLength;
	}

	@Override
	public String toString() {
		try {
			return InetAddress.getByAddress(this.address).getHostAddress() + "/" + this.prefixLength;
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
