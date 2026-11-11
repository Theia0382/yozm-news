package net.yozmnews.tag.domain.tagset;

import java.util.List;
import java.util.Objects;

import net.yozmnews.tag.LineageKind;
import net.yozmnews.tag.TagKind;

/**
 * 계보 항목 하나 — 기준 버전의 이전 키가 새 버전의 어느 키로 이어지는지 — docs/specs/tag.md §4 · tag-R22.
 * <p>
 * 원문의 {@code lineage} 절 항목이자 저장된 버전의 계보다. 키는 그 태그 종류의 키다(중분류는 전체 키).
 */
public record LineageDef(TagKind kind, LineageKind change, List<TagKey> from, List<TagKey> to) {

	public LineageDef {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(change, "change");
		from = List.copyOf(from);
		to = List.copyOf(to);
	}

}
