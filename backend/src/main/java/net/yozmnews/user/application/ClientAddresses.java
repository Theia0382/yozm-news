package net.yozmnews.user.application;

import jakarta.servlet.http.HttpServletRequest;
import net.yozmnews.user.domain.guard.ClientAddress;

/**
 * 요청의 클라이언트 주소 — 배포 proxy 가 정해 덮어쓴 헤더 {@value #HEADER} 의 주소, 없거나 읽지 못하면 직접 연결한 주소(개발 · 시험).
 * docs/specs/user.md §4 · user-R37
 * <p>
 * 앱은 proxy 뒤에만 있어 이 헤더를 믿는다 — 신뢰할 앞단 범위와 읽는 방향은 proxy 설정이 정한다. 헤더 이름은 이 상수가 코드 쪽 정본이고,
 * proxy 설정(Caddyfile)과의 일치는 시험이 본다.
 */
final class ClientAddresses {

	/** proxy 가 클라이언트 주소를 덮어써 넘기는 헤더 */
	static final String HEADER = "X-Real-IP";

	private ClientAddresses() {
	}

	/** 이 요청의 클라이언트 주소 */
	static ClientAddress of(HttpServletRequest request) {
		return ClientAddress.parse(request.getHeader(HEADER))
			.or(() -> ClientAddress.parse(request.getRemoteAddr()))
			.orElseThrow(() -> new IllegalStateException("요청의 직접 연결 주소가 IP 리터럴이 아니다"));
	}

}
