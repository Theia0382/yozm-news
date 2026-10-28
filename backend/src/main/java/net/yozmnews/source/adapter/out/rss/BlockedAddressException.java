package net.yozmnews.source.adapter.out.rss;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 이름이 차단 대역의 주소로 해석됐다 — 연결을 시작하지 않는다. Apache 연결 경로가 이름 해석 실패로 그대로 올려 보내도록
 * {@link UnknownHostException} 을 잇는다.
 */
final class BlockedAddressException extends UnknownHostException {

	private static final long serialVersionUID = 1L;

	private final transient InetAddress address;

	BlockedAddressException(String host, InetAddress address) {
		super("차단된 주소: " + host + " → " + address.getHostAddress());
		this.address = address;
	}

	InetAddress address() {
		return this.address;
	}

}
