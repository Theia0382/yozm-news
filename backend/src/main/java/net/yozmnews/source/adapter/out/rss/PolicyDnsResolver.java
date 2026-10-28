package net.yozmnews.source.adapter.out.rss;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

import net.yozmnews.source.domain.fetch.AddressPolicy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * 해석 결과를 {@link AddressPolicy} 로 거르는 이름 해석기. Apache 는 연결할 때마다 이 해석기가 돌려준 주소로만 연결하므로,
 * 검사한 주소가 곧 연결 주소다. IP 리터럴도 이 해석기를 거친다. Apache 5.6.5 의 연결 경로는
 * {@code resolve(String, int)} 를 부르고 그 기본 구현이 {@link #resolve(String)} 을 부른다.
 */
final class PolicyDnsResolver implements DnsResolver {

	private final AddressPolicy policy;

	PolicyDnsResolver(AddressPolicy policy) {
		this.policy = Objects.requireNonNull(policy, "policy");
	}

	/**
	 * @throws BlockedAddressException 해석 결과 가운데 막힌 주소가 하나라도 있을 때 — 이름 전체를 거부한다
	 * @throws UnknownHostException 이름을 해석하지 못했을 때
	 */
	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {
		InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
		var blocked = this.policy.firstBlocked(Arrays.asList(addresses));
		if (blocked.isPresent()) {
			throw new BlockedAddressException(host, blocked.get());
		}
		return addresses;
	}

	/** 수집은 인증 체계를 쓰지 않아 연결 경로가 부르지 않는다 — 기본 해석기에 맡긴다 */
	@Override
	public String resolveCanonicalHostname(String host) throws UnknownHostException {
		return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
	}

}
