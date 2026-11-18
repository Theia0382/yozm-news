package net.yozmnews.digest.domain.selection;

import java.util.List;
import java.util.Objects;

/**
 * 고른 후보 — 큰 슬롯 기사와 구독 후보 — docs/specs/digest.md §5
 *
 * @param big 큰 슬롯 기사 — 행 순서, 행 안에서는 사건 순서
 * @param subscribed 구독 후보 — 순위 순서
 * @param bigRows {@code big} 과 같은 순서로 각 큰 슬롯 기사가 나온 큰 판정 행의 위치({@link BigSlotPicker.BigSlots#rows})
 */
public record Shortlist(List<ArticleFacts> big, List<ArticleFacts> subscribed, List<Integer> bigRows) {

	/**
	 * @throws IllegalArgumentException 행 위치가 큰 슬롯 기사마다 하나가 아닐 때
	 */
	public Shortlist {
		big = List.copyOf(Objects.requireNonNull(big, "big"));
		subscribed = List.copyOf(Objects.requireNonNull(subscribed, "subscribed"));
		bigRows = List.copyOf(Objects.requireNonNull(bigRows, "bigRows"));
		if (bigRows.size() != big.size()) {
			throw new IllegalArgumentException("행 위치가 큰 슬롯 기사마다 하나가 아니다: " + big.size() + " · " + bigRows.size());
		}
	}

}
