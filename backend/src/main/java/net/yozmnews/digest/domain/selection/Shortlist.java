package net.yozmnews.digest.domain.selection;

import java.util.List;
import java.util.Objects;

/**
 * 고른 후보 — 큰 슬롯 사건과 구독 사건 — docs/specs/digest.md §5
 *
 * @param big 큰 슬롯 사건 — 행 순서, 행 안에서는 사건 순서
 * @param subscribed 구독 사건 — 순위 순서
 * @param followed 주제 구독 밖 관심 흐름의 길로 든 사건 — 순위 순서({@link FollowedStorylines})
 * @param bigRows {@code big} 과 같은 순서로 각 큰 슬롯 사건이 나온 큰 판정 행의 위치({@link BigSlotPicker.BigSlots#rows})
 */
public record Shortlist(List<CandidateEvent> big, List<CandidateEvent> subscribed, List<CandidateEvent> followed,
		List<Integer> bigRows) {

	/**
	 * @throws IllegalArgumentException 행 위치가 큰 슬롯 사건마다 하나가 아닐 때
	 */
	public Shortlist {
		big = List.copyOf(Objects.requireNonNull(big, "big"));
		subscribed = List.copyOf(Objects.requireNonNull(subscribed, "subscribed"));
		followed = List.copyOf(Objects.requireNonNull(followed, "followed"));
		bigRows = List.copyOf(Objects.requireNonNull(bigRows, "bigRows"));
		if (bigRows.size() != big.size()) {
			throw new IllegalArgumentException("행 위치가 큰 슬롯 사건마다 하나가 아니다: " + big.size() + " · " + bigRows.size());
		}
	}

}
