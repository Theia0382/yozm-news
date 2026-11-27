package net.yozmnews.digest.domain.item;

import java.util.List;
import java.util.Objects;

/**
 * 가르기 결과 — {@link ItemPlan#arrange} 가 낸다 · docs/specs/digest.md §5 · digest-R47
 *
 * @param items 종류를 가른 항목 — 주요 뉴스 항목(항목 순위) 다음 헤드라인 항목(항목 순위)
 * @param shortSummary 요약문이 짧은 항목 수 — 항목의 요약문 길이(RSS 요약문)가 기준보다 짧아 헤드라인이 된 항목 가운데, 기준 이상이었으면
 *     주요 뉴스가 되었을 항목의 수. 큰 판정 나무의 그런 항목과, 큰 화제 밖 주요 뉴스 자리가 남아 있는 동안 순위로 만난 그런 항목의 합이다
 */
public record ItemArrangement(List<ArrangedItem> items, int shortSummary) {

	/**
	 * @throws IllegalArgumentException 요약문이 짧은 항목 수가 음수이거나 헤드라인 항목 수보다 클 때
	 */
	public ItemArrangement {
		items = List.copyOf(Objects.requireNonNull(items, "items"));
		long headlines = items.stream().filter(item -> item.kind() == ItemKind.HEADLINE).count();
		if (shortSummary < 0 || shortSummary > headlines) {
			throw new IllegalArgumentException(
					"요약문이 짧은 항목 수가 헤드라인 항목 수 " + headlines + " 의 범위 밖이다: " + shortSummary);
		}
	}

}
