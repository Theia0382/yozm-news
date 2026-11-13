package net.yozmnews.user.domain.session;

import java.time.Duration;

/**
 * 세션 수명의 정본 — 마지막 요청 뒤의 비활성 기간이고 절대 상한이 없다. docs/specs/user.md §4 · user-R8
 */
public final class SessionLifetime {

	/** 로그인 유지를 켠 세션 — 지속 쿠키 */
	public static final Duration KEEP = Duration.ofDays(7);

	/** 로그인 유지를 끈 세션 — 브라우저 세션 쿠키 */
	public static final Duration BRIEF = Duration.ofHours(2);

	private SessionLifetime() {
	}

	/** 로그인 유지 여부에 맞는 비활성 기간 */
	public static Duration of(boolean keep) {
		return keep ? KEEP : BRIEF;
	}

}
