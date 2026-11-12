package net.yozmnews.storyline.domain.big;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.platform.ChangeActor;

/**
 * 큰 판정 기준 저장소 — 지금 값 한 행과 추가 전용 이력 · docs/specs/storyline.md §4 · storyline-R20.
 * <p>
 * 쓰기는 부르는 쪽의 트랜잭션 안에서 한다.
 */
public interface BigStorylineCriteriaRepository {

	/**
	 * 지금 값이 없으면 넣고, 넣었으면 같은 트랜잭션에서 이력(행위자 {@code SYSTEM}, 이전 값 없음) 한 행을 넣는다. 동시에 불러도 한 행이다.
	 * @return 넣었으면 true
	 */
	boolean seedIfAbsent(BigStorylineCriteria criteria, Instant now);

	/** 지금 값 — 처음 값을 넣기 전이면 빈 값 */
	Optional<BigStorylineCriteria> current();

	/**
	 * 지금 값을 잠가 읽는다({@code FOR UPDATE}) — 트랜잭션 안에서만 부른다.
	 * @throws IllegalStateException 처음 값을 넣기 전일 때
	 */
	BigStorylineCriteria lockCurrent();

	/** 지금 값을 바꾸고 이력 한 행을 넣는다 — {@link #lockCurrent} 뒤 같은 트랜잭션에서 */
	void change(BigStorylineCriteria previous, BigStorylineCriteria next, ChangeActor actor, Instant now);

	/** 이력 — 최근 것부터, 같은 시각이면 나중에 넣은 것부터 */
	List<BigStorylineCriteriaChange> changes();

}
