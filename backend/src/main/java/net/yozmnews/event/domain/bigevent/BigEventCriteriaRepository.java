package net.yozmnews.event.domain.bigevent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.BigEventCriteriaChange;

/**
 * 큰 사건 기준 저장소 — 지금 값 한 행과 추가 전용 이력 · docs/specs/event.md §4 · event-R21.
 * <p>
 * 쓰기는 부르는 쪽의 트랜잭션 안에서 한다.
 */
public interface BigEventCriteriaRepository {

	/**
	 * 지금 값이 없으면 넣고, 넣었으면 같은 트랜잭션에서 이력(행위자 {@code SYSTEM}, 이전 값 없음) 한 행을 넣는다. 동시에 불러도 한 행이다.
	 * @return 넣었으면 true
	 */
	boolean seedIfAbsent(BigEventCriteria criteria, Instant now);

	/** 지금 값 — 처음 값을 넣기 전이면 빈 값 */
	Optional<BigEventCriteria> current();

	/**
	 * 지금 값을 잠가 읽는다({@code FOR UPDATE}) — 트랜잭션 안에서만 부른다.
	 * @throws IllegalStateException 처음 값을 넣기 전일 때
	 */
	BigEventCriteria lockCurrent();

	/** 지금 값을 바꾸고 이력 한 행을 넣는다 — {@link #lockCurrent} 뒤 같은 트랜잭션에서 */
	void change(BigEventCriteria previous, BigEventCriteria next, ChangeActor actor, Instant now);

	/** 이력 — 최근 것부터, 같은 시각이면 나중에 넣은 것부터 */
	List<BigEventCriteriaChange> changes();

}
