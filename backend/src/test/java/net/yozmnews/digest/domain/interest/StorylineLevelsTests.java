package net.yozmnews.digest.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * 스토리라인 관심 — 가중치 변환, 거른 꼭대기(x ≤ −0.2), 관심 스토리라인(x ≥ 0.35 ∪ 구독 − 거른 꼭대기), 남길 스토리라인(x ≥ 0.2).
 * <p>
 * 지키는 규칙: digest-R49 · digest-R51
 */
class StorylineLevelsTests {

	private static final Offset<Double> EXACT = within(1e-12);

	private static final StorylineId A = new StorylineId(1);

	private static final StorylineId B = new StorylineId(2);

	private static final StorylineId C = new StorylineId(3);

	private static final StorylineId D = new StorylineId(4);

	@Test
	void theWeightIsOnePlusHalfTheInterestAndAnEventAveragesItsTops() {
		StorylineInterests weights = new StorylineLevels(Map.of(A, 0.25, B, -0.5)).weights();

		// g = 1 + 0.5 × 0.25 = 1.125 · 1 + 0.5 × (−0.5) = 0.75, 신호 없는 꼭대기는 1
		assertThat(weights.weight(A)).isCloseTo(1.125, EXACT);
		assertThat(weights.weight(B)).isCloseTo(0.75, EXACT);
		assertThat(weights.weight(C)).isEqualTo(1.0);
		// 사건의 가중치는 꼭대기 가중치의 평균 — (1.125 + 0.75) ÷ 2 = 0.9375, 꼭대기가 없으면 1
		assertThat(weights.weightOf(List.of(A, B))).isCloseTo(0.9375, EXACT);
		assertThat(weights.weightOf(List.of())).isEqualTo(1.0);
	}

	@Test
	void topsAtOrBelowTheExclusionThresholdAreExcluded() {
		StorylineLevels levels = new StorylineLevels(Map.of(A, -0.2, B, -0.19999, C, -0.25));

		assertThat(levels.excluded()).containsExactlyInAnyOrder(A, C);
	}

	@Test
	void followedStorylinesAreTopsAtTheFollowThresholdOrSubscribedLessTheExcluded() {
		StorylineLevels levels = new StorylineLevels(Map.of(A, 0.35, B, 0.349, C, -0.2));

		// 대조 — 0.349 는 관심 스토리라인이 아니다
		assertThat(levels.followed(Set.of())).containsExactly(A);
		// 구독한 B 는 관심 스토리라인, 구독한 C 는 거른 꼭대기라 아니다(거름이 앞선다), 신호 없는 구독 D 는 관심 스토리라인
		assertThat(levels.followed(Set.of(B, C, D))).containsExactlyInAnyOrder(A, B, D);
	}

	@Test
	void keptStorylinesAreTopsAtTheKeepThreshold() {
		assertThat(new StorylineLevels(Map.of(A, 0.2, B, 0.19999)).kept()).containsExactly(A);
	}

	@Test
	void levelsOutsideTheRangeAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylineLevels(Map.of(A, 1.5)));
		assertThatIllegalArgumentException().isThrownBy(() -> new StorylineLevels(Map.of(A, Double.NaN)));
	}


	@Test
	void aTopInSeveralStorylinesWeighsByTheMeanAndIsExcludedOnlyWhenAllAreExcluded() {
		StorylineId shared = new StorylineId(5);
		// 묶음 값 0.4 · −0.3 → 관심(평균) 0.05 · 가중치 1.025, 거름 아님(−0.3 하나만), 관심 스토리라인 · 남길 스토리라인(0.4 가 0.35 · 0.2 이상)
		StorylineLevels mixed = StorylineLevels.ofUserStorylines(Map.of(shared, List.of(0.4, -0.3)));
		assertThat(mixed.level(shared)).isCloseTo(0.05, within(1e-12));
		assertThat(mixed.weights().weight(shared)).isCloseTo(1.025, within(1e-12));
		assertThat(mixed.excluded()).isEmpty();
		assertThat(mixed.followed(Set.of())).containsExactly(shared);
		assertThat(mixed.kept()).containsExactly(shared);

		// 묶음 값 −0.3 · −0.25 → 모두 −0.2 이하 → 거름, 관심 스토리라인에서도 빠진다
		StorylineLevels allExcluded = StorylineLevels.ofUserStorylines(Map.of(shared, List.of(-0.3, -0.25)));
		assertThat(allExcluded.excluded()).containsExactly(shared);
		assertThat(allExcluded.followed(Set.of(shared))).isEmpty();
		// 평균 −0.2 지만 하나가 −0.1 이면 거름이 아니다
		assertThat(StorylineLevels.ofUserStorylines(Map.of(shared, List.of(-0.3, -0.1))).excluded()).isEmpty();
	}

	@Test
	void aTopInOneStorylineIsTheSameAsTheSingleValueConstructor() {
		// 대조 — 묶음 하나면 지금의 꼴과 같다
		StorylineId top = new StorylineId(7);
		assertThat(StorylineLevels.ofUserStorylines(Map.of(top, List.of(-0.25)))).isEqualTo(new StorylineLevels(Map.of(top, -0.25)));
		assertThat(StorylineLevels.ofUserStorylines(Map.of(top, List.of()))).as("빈 목록은 신호 없음").isEqualTo(StorylineLevels.NONE);
	}
}
