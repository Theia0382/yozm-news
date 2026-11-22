package net.yozmnews.user.domain.userstoryline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 사용자 스토리라인의 씨앗 꼭대기.
 * <p>
 * 지키는 규칙: 씨앗은 반응 · 추적과 해제 이력 · 추적 씨앗의 꼭대기이고 노출은 재료가 아니다(docs/specs/user.md §5)
 */
class UserStorylineSeedsTests {

	@Test
	void seedsAreTheTopsOfReactionsSubscriptionChangesAndTrackedSeeds() {
		// 반응 1 · 2, 추적 · 해제 이력 2 · 3, 추적 씨앗 4
		assertThat(UserStorylineSeeds.tops(List.of(1L, 2L), List.of(2L, 3L), List.of(4L)))
			.containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
	}

	@Test
	void withoutAnySignalThereIsNoSeed() {
		// 대조 — 노출만 받은 흐름은 재료에 오지 않는다. 신호가 없으면 씨앗도 없다
		assertThat(UserStorylineSeeds.tops(List.of(), List.of(), List.of())).isEmpty();
	}

	@Test
	void aTrackedSeedAloneIsASeed() {
		// 창 안에 신호가 없어도 추적 씨앗은 씨앗이다
		assertThat(UserStorylineSeeds.tops(List.of(), List.of(), List.of(7L))).containsExactly(7L);
	}

}
