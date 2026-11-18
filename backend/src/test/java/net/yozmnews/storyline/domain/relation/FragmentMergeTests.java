package net.yozmnews.storyline.domain.relation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.storyline.domain.relation.RelationRule.Decision;
import org.junit.jupiter.api.Test;

/**
 * 같은 사안의 조각으로 판정된 두 스토리라인을 합칠지 — 조각 답의 확률이 합치기 확률 기준 이상이면 합치고 ID 가 작은 쪽이 남는다.
 * <p>
 * 기준: 확률 0.60 · 합치기 확률 0.80.
 * <p>
 * 지키는 규칙: storyline-R17
 */
class FragmentMergeTests {

	private static final RelationCriteria CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14), 0.80);

	@Test
	void mergesAtOrAboveTheMergeProbabilityAndKeepsBelowIt() {
		assertThat(FragmentMerge.decide(new Decision.Fragments(3, 7, 0.80), CRITERIA))
			.isEqualTo(new FragmentMerge.Action.Merge(3, 7));
		assertThat(FragmentMerge.decide(new Decision.Fragments(3, 7, 0.95), CRITERIA))
			.isEqualTo(new FragmentMerge.Action.Merge(3, 7));
		// 관계의 확률 기준은 넘었지만 합치기 기준에는 못 미친다 — 합치지 않는다
		assertThat(FragmentMerge.decide(new Decision.Fragments(3, 7, 0.7999), CRITERIA))
			.isEqualTo(new FragmentMerge.Action.Keep());
	}

	@Test
	void theSmallerIdSurvivesWhicheverSideItIsOn() {
		assertThat(FragmentMerge.decide(new Decision.Fragments(7, 3, 0.9), CRITERIA))
			.isEqualTo(new FragmentMerge.Action.Merge(3, 7));
		assertThat(FragmentMerge.decide(new Decision.Fragments(3, 7, 0.9), CRITERIA))
			.isEqualTo(new FragmentMerge.Action.Merge(3, 7));
	}

}
