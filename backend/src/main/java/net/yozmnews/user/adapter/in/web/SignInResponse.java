package net.yozmnews.user.adapter.in.web;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * 로그인 결과 — 판정 결과 200. 계정 유무와 무관하게 같은 본문이다. docs/specs/user.md §3 · user-R17
 *
 * @param result 로그인됨 · 거절 · 대기
 * @param retryAfterSeconds 대기면 다시 시도할 수 있을 때까지 남은 초(올림), 그 밖 null
 */
record SignInResponse(SignInResult result, @Nullable Long retryAfterSeconds) {

	static SignInResponse signedIn() {
		return new SignInResponse(SignInResult.SIGNED_IN, null);
	}

	static SignInResponse rejected() {
		return new SignInResponse(SignInResult.REJECTED, null);
	}

	/** 대기 — 남은 시간을 초로 올림한다(1 이상) */
	static SignInResponse waiting(Instant retryAt, Instant now) {
		Duration remaining = Duration.between(now, retryAt);
		long seconds = remaining.toSeconds() + ((remaining.toNanosPart() > 0) ? 1 : 0);
		return new SignInResponse(SignInResult.WAIT, Math.max(1, seconds));
	}

	/** 로그인 결과의 판별자 */
	enum SignInResult {

		/** 로그인됐다 */
		SIGNED_IN,

		/** 이메일 또는 비밀번호가 맞지 않는다 */
		REJECTED,

		/** 연속 실패로 기다려야 한다 */
		WAIT

	}

}
