package net.yozmnews.storyline;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import net.yozmnews.event.EventTally;

/**
 * 조회 기간의 큰 스토리라인 하나 · docs/specs/storyline.md §3 · storyline-R19.
 * <p>
 * 큰 판정 목록은 포함 나무마다 한 행이다 — 큰 스토리라인 가운데 그 나무에서 가장 위의 것이 행이고, 그 아래의 큰 스토리라인은 행의
 * {@code branches} 에 실린다.
 *
 * @param tally 조회 기간의 기간 집계
 * @param intensity 조회 기간의 보도 강도 — 소수 둘째 자리(storyline-R19)
 * @param branches 이 행 아래 포함 나무의 큰 스토리라인 — 보도 강도 내림차순 · ID 오름차순. 여기 실린 것의 {@code branches} 는 비어
 * 있다
 */
public record BigStoryline(StorylineView storyline, EventTally tally, BigDecimal intensity, List<BigStoryline> branches) {

	/**
	 * @throws IllegalArgumentException 아래 행이 다시 아래 행을 가질 때
	 */
	public BigStoryline {
		Objects.requireNonNull(storyline, "storyline");
		Objects.requireNonNull(tally, "tally");
		Objects.requireNonNull(intensity, "intensity");
		branches = List.copyOf(branches);
		if (branches.stream().anyMatch(branch -> !branch.branches().isEmpty())) {
			throw new IllegalArgumentException("아래 행은 다시 아래 행을 갖지 않는다: " + storyline.id());
		}
	}

	/** 아래 행이 없는 큰 스토리라인 */
	public BigStoryline(StorylineView storyline, EventTally tally, BigDecimal intensity) {
		this(storyline, tally, intensity, List.of());
	}

}
