package net.yozmnews.digest.domain.selection;

import java.util.List;
import java.util.Objects;

/**
 * 고른 후보 — 큰 슬롯 기사와 구독 후보 — docs/specs/digest.md §5
 *
 * @param big 큰 슬롯 기사 — 행 순서, 행 안에서는 사건 순서
 * @param subscribed 구독 후보 — 순위 순서
 */
public record Shortlist(List<ArticleFacts> big, List<ArticleFacts> subscribed) {

	public Shortlist {
		big = List.copyOf(Objects.requireNonNull(big, "big"));
		subscribed = List.copyOf(Objects.requireNonNull(subscribed, "subscribed"));
	}

}
