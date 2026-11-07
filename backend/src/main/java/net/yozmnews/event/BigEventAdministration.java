package net.yozmnews.event;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.evaluation.ChangeActor;

/**
 * 큰 사건 기준의 운영 — 지금 값 · 변경 · 이력 · docs/specs/event.md §3 · event-R21.
 * <p>
 * {@code admin} 이 운영자 요청으로 부른다. 큰 사건은 조회할 때 지금 기준으로 판정하므로(event-R15) 바꾸면 다음 조회부터 반영된다.
 */
public interface BigEventAdministration {

	/**
	 * 지금 기준.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	BigEventCriteria criteria();

	/**
	 * 기준을 바꾼다 — 한 트랜잭션. 동시 변경은 줄을 서므로 이력의 이전 값이 바로 앞 변경의 새 값이다.
	 * @return 범위 밖이면 {@code OutOfRange}, 지금 값과 같으면 {@code Unchanged} — 둘 다 아무것도 쓰지 않는다
	 * @throws IllegalStateException 범위 안의 값인데 처음 값을 적재하기 전일 때
	 */
	BigEventCriteriaChangeResult change(int minSources, BigDecimal minImportance, ChangeActor actor);

	/** 이력 — 최근 것부터(같은 시각이면 나중에 쓴 것부터) */
	List<BigEventCriteriaChange> changes();

}
