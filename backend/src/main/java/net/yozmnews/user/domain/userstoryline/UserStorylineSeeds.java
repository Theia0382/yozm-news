package net.yozmnews.user.domain.userstoryline;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 사용자 스토리라인의 씨앗 꼭대기 — 정본 · docs/specs/user.md §5 · user-R66.
 * <p>
 * 씨앗은 사용자가 관심 신호를 준 흐름이다 — 창 안에 반응을 받은 사건의 지금 꼭대기, 창 안의 사용자 원인 추적 · 해제 이력의 지금 꼭대기, 추적
 * 씨앗의 지금 꼭대기. 노출은 재료가 아니다. 추적 씨앗은 창과 무관하다 — 추적 중인 한 남는다. 창으로 자르는 일과 꼭대기로 푸는 일은 부르는
 * 쪽이 한다.
 */
public final class UserStorylineSeeds {

	private UserStorylineSeeds() {
	}

	/**
	 * @param reactedTops 창 안에 반응(좋아요 · 싫어요 · 되돌리기)을 받은 사건의 지금 꼭대기
	 * @param changedTops 창 안의 사용자 원인 추적 · 해제 이력의 스토리라인의 지금 꼭대기
	 * @param trackedSeedTops 추적 씨앗의 지금 꼭대기
	 * @return 씨앗 꼭대기 — 셋의 합집합
	 */
	public static Set<Long> tops(Collection<Long> reactedTops, Collection<Long> changedTops,
			Collection<Long> trackedSeedTops) {
		Objects.requireNonNull(reactedTops, "reactedTops");
		Objects.requireNonNull(changedTops, "changedTops");
		Objects.requireNonNull(trackedSeedTops, "trackedSeedTops");
		Set<Long> seeds = new TreeSet<>(reactedTops);
		seeds.addAll(changedTops);
		seeds.addAll(trackedSeedTops);
		return Set.copyOf(seeds);
	}

}
