package net.yozmnews.tag;

import java.util.List;
import java.util.Objects;

/**
 * 계보 항목 하나 — 기준 버전의 이전 키와 새 버전의 새 키 — docs/specs/tag.md §3 · tag-R22
 */
public record TagLineageView(TagKind kind, LineageKind change, List<String> from, List<String> to) {

	public TagLineageView {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(change, "change");
		from = List.copyOf(from);
		to = List.copyOf(to);
	}

}
