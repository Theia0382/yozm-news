package net.yozmnews.user.domain.throttle;

import java.time.Instant;

/**
 * 로그인 실패 기록의 저장 인터페이스 — 구현은 {@code adapter/out/persistence} 의 {@code JdbcLoginFailures}. docs/specs/user.md §4 ·
 * user-R18 · user-R19
 */
public interface LoginFailures {

	/**
	 * 이 이메일의 기록을 잠그고 읽는다 — 없으면 실패 0 회의 자리 행을 넣고 잠근다. 트랜잭션 안에서 부르고, 같은 트랜잭션이 {@link #record}
	 * 나 {@link #forget} 으로 끝낸다(자리 행이 커밋 뒤에 남지 않게).
	 */
	FailureRecord lock(EmailDigest digest);

	/** 실패 기록을 쓴다 */
	void record(EmailDigest digest, FailureRecord record);

	/** 기록을 지운다 */
	void forget(EmailDigest digest);

	/**
	 * 마지막 실패가 기준 시각보다 이른 기록을 지운다.
	 * @return 지운 행 수
	 */
	int forgetOlderThan(Instant cutoff);

}
