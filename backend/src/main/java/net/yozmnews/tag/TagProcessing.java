package net.yozmnews.tag;

import java.util.List;

import net.yozmnews.platform.RoundQueue;

/**
 * 태그 쪽 회차의 처리 현황 — 평가 대기의 남은 수 · 미룬 수와 회차 기록의 최근 합 · docs/specs/tag.md §3.
 */
public interface TagProcessing {

	/** 회차 하나의 처리 현황 — 태그 평가({@code tag-evaluation}) */
	List<RoundQueue> rounds();

}
