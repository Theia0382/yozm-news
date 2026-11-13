package net.yozmnews.storyline;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.event.EventPeriod;
import net.yozmnews.platform.ChangeActor;

/**
 * 큰 판정 기준의 운영 — 지금 값 · 변경 · 이력 · docs/specs/storyline.md §3 · storyline-R20.
 * <p>
 * {@code admin} 이 운영자 요청으로 부른다. 큰 스토리라인은 조회할 때 지금 기준으로 판정하므로(storyline-R19) 바꾸면 다음 조회부터 반영된다.
 */
public interface BigStorylineAdministration {

	/**
	 * 지금 기준.
	 * @throws IllegalStateException 처음 값을 적재하기 전일 때
	 */
	BigStorylineCriteria criteria();

	/**
	 * 기준을 바꾼다 — 한 트랜잭션. 동시 변경은 줄을 서므로 이력의 이전 값이 바로 앞 변경의 새 값이다.
	 * @return 범위 밖이면 {@code OutOfRange}, 지금 값과 같으면 {@code Unchanged} — 둘 다 아무것도 쓰지 않는다
	 * @throws IllegalStateException 범위 안의 값인데 처음 값을 적재하기 전일 때
	 */
	BigStorylineCriteriaChangeResult change(BigDecimal minIntensity, ChangeActor actor);

	/** 이력 — 최근 것부터(같은 시각이면 나중에 쓴 것부터) */
	List<BigStorylineCriteriaChange> changes();

	/**
	 * 미리보기 — 제안 기준으로 판정한 조회 기간의 큰 스토리라인. 판정 · 순서는 {@link StorylineDirectory#bigStorylines} 와 같고, 아무것도 쓰지
	 * 않는다.
	 */
	List<BigStoryline> preview(BigStorylineCriteria proposed, EventPeriod period);

}
