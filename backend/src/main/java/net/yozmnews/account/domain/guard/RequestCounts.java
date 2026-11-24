package net.yozmnews.account.domain.guard;

import java.time.Instant;

/**
 * 요청 제한 기록의 저장 인터페이스 — 키와 창마다 요청 수 하나. 구현은 {@code adapter/out/persistence} 의 {@code JdbcRequestCounts}.
 * docs/specs/user.md §4 · user-R39 · user-R42
 */
public interface RequestCounts {

	/**
	 * 이 키 · 창의 요청 수에 1을 더하고 더한 값을 돌려준다 — 없으면 1로 만든다. 여러 프로세스가 함께 불러도 더하기가 빠지지 않는다.
	 * @return 이 요청을 더한 수(1 이상)
	 */
	int increment(LimitKey key, LimitWindow window);

	/**
	 * 끝이 기준 시각보다 이른 창의 기록을 지운다.
	 * @return 지운 행 수
	 */
	int forgetEndedBefore(Instant cutoff);

}
