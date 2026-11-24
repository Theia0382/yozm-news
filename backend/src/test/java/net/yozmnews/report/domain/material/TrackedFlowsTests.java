package net.yozmnews.report.domain.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.StorylineSubscriptionChange;
import org.junit.jupiter.api.Test;

/**
 * 기간 안 추적 — 사용자가 더한 행만, 지금 스토리라인을 꼭대기로 올려 흐름에 잇는다. 지키는 규칙: report-R16
 */
class TrackedFlowsTests {

	private static final Instant AT = Instant.parse("2026-11-18T00:00:00Z");

	@Test
	void marksOnlyFlowsTheUserStartedTracking() {
		StorylineId seed = new StorylineId(5);
		StorylineId top = new StorylineId(4);
		StorylineId split = new StorylineId(8);
		StorylineId removed = new StorylineId(9);
		Map<StorylineId, Set<StorylineId>> flows = Map.of(new StorylineId(6), Set.of(top, new StorylineId(6)),
				new StorylineId(8), Set.of(split), new StorylineId(9), Set.of(removed));
		List<StorylineSubscriptionChange> changes = List.of(
				new StorylineSubscriptionChange(seed, seed, StorylineSubscriptionAction.ADD,
						StorylineSubscriptionCause.USER, AT),
				new StorylineSubscriptionChange(split, split, StorylineSubscriptionAction.ADD,
						StorylineSubscriptionCause.STORYLINE_SPLIT, AT),
				new StorylineSubscriptionChange(removed, removed, StorylineSubscriptionAction.REMOVE,
						StorylineSubscriptionCause.USER, AT),
				new StorylineSubscriptionChange(new StorylineId(99), null, StorylineSubscriptionAction.ADD,
						StorylineSubscriptionCause.USER, AT));

		Set<StorylineId> started = TrackedFlows.startedIn(changes, Map.of(seed, top, split, split, removed, removed),
				flows);

		// 씨앗 5 의 꼭대기 4 를 가진 흐름(헤드 6)만 — 갈라짐 · 뺌 · 지운 스토리라인은 아니다
		assertThat(started).containsExactly(new StorylineId(6));
	}

}
