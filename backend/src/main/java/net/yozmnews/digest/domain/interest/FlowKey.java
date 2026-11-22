package net.yozmnews.digest.domain.interest;

import net.yozmnews.storyline.StorylineId;

/**
 * 반복을 가르는 흐름의 키 — 꼭대기를 추적 대상으로 가진 사용자 스토리라인 묶음이 있으면 그 묶음, 없으면 꼭대기 자신. docs/specs/digest.md §5
 * <p>
 * 스토리라인 관심의 키는 {@link Flow} 뿐이다 — {@link Top} 은 반복 판정에만 쓴다.
 */
public sealed interface FlowKey permits FlowKey.Flow, FlowKey.Top {

	/** 사용자 스토리라인 묶음 — 추적 중인지와 무관하다 */
	record Flow(int flow) implements FlowKey {
	}

	/** 묶음이 없는 꼭대기 */
	record Top(StorylineId top) implements FlowKey {
	}

	/** 그 꼭대기의 키 */
	static FlowKey of(StorylineId top, StorylineFlows flows) {
		return flows.flowOf(top).<FlowKey>map(Flow::new).orElseGet(() -> new Top(top));
	}

}
