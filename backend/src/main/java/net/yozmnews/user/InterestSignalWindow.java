package net.yozmnews.user;

import java.time.Instant;

/**
 * 관심 신호를 세는 창 — 이 모듈이 선언하고 {@code digest} 가 구현한다. 사용자 스토리라인의 씨앗이 되는 신호(반응 · 추적과 해제)를 어디까지
 * 거슬러 읽을지 정한다. docs/specs/user.md §3
 * <p>
 * 창의 길이는 관심도의 것이다 — 이 모듈은 값을 갖지 않고 {@code digest} 의 타입을 쓰지 않는다.
 */
public interface InterestSignalWindow {

	/**
	 * @param reference 기준 시각
	 * @return 기준 시각의 관심이 세는 신호 가운데 가장 이른 시각(창의 시작)
	 */
	Instant start(Instant reference);

}
