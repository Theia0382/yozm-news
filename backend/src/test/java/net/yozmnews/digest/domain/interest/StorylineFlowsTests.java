package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import org.junit.jupiter.api.Test;

/**
 * 사용자 스토리라인 묶음 — 갈라지기 전 꼭대기는 묶음 여럿에 든다.
 * <p>
 * 지키는 규칙: digest-R49 · digest-R51
 */
class StorylineFlowsTests {

	private static final StorylineId SHARED = new StorylineId(5);

	private static final StorylineId BRANCH = new StorylineId(82);

	private static final StorylineId SIBLING = new StorylineId(307);

	@Test
	void aTopIsTrackedWhenAnyOfItsFlowsIsTracked() {
		// 묶음 0 = {5, 82} 추적, 묶음 1 = {5, 307} 추적 아님 → 5 · 82 는 추적 꼭대기, 307 은 아니다
		StorylineFlows flows = new StorylineFlows(Map.of(SHARED, Set.of(0, 1), BRANCH, Set.of(0), SIBLING, Set.of(1)),
				Set.of(0));

		assertThat(flows.trackedTops()).containsExactlyInAnyOrder(SHARED, BRANCH);
		assertThat(flows.flowsOf(SHARED)).containsExactlyInAnyOrder(0, 1);
		assertThat(flows.flowsOf(new StorylineId(999))).as("묶음 없는 꼭대기").isEmpty();
	}

	@Test
	void anEmptyFlowSetOrATrackedFlowWithoutTopsIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylineFlows(Map.of(SHARED, Set.of()), Set.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylineFlows(Map.of(SHARED, Set.of(0)), Set.of(1)));
	}

}
