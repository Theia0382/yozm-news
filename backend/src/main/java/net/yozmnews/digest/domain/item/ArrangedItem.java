package net.yozmnews.digest.domain.item;

import java.util.Objects;

/**
 * 종류를 가른 항목 — {@link ItemPlan#arrange} 가 낸다 · docs/specs/digest.md §5
 *
 * @param plan 항목 — 사건은 사건 점수 순
 * @param kind 주요 뉴스 · 헤드라인
 */
public record ArrangedItem(DigestItemPlan plan, ItemKind kind) {

	public ArrangedItem {
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(kind, "kind");
	}

}
