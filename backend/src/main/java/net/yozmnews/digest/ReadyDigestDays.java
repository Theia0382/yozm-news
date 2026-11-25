package net.yozmnews.digest;

import java.time.Instant;

import net.yozmnews.platform.UserId;

/**
 * READY 다이제스트의 날짜 수 — 변화 리포트의 일정이 주간 리포트를 만들지 정하려고 읽는다. docs/specs/digest.md §3
 * <p>
 * 사용자를 인자로 받는 조회다. 읽기 전용 트랜잭션 하나 · 질의 하나다. 쓰지 않는다.
 */
public interface ReadyDigestDays {

	/**
	 * 그 사용자의 {@code READY} 다이제스트 가운데 기간 끝(일정이면 생성 시각, 수동이면 요청 시각 — 사용자에게 간 때)이 {@code from} 이상 ·
	 * {@code to} 미만인 것의 서로 다른 다이제스트 날짜 수 — 같은 날짜의 행이 여럿이어도 하나로 센다.
	 * @throws IllegalArgumentException {@code to} 가 {@code from} 보다 뒤가 아닐 때
	 */
	int count(UserId user, Instant from, Instant to);

}
