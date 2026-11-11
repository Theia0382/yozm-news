package net.yozmnews.tag.domain.tagset;

import java.util.Objects;

import net.yozmnews.tag.LineageKind;
import net.yozmnews.tag.TagKind;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 버전의 계보 한 행 — aggregate {@link TagSet} 의 자식 · 이전 키 하나와 새 키 하나의 쌍 — docs/specs/tag.md §6.
 * <p>
 * 분할은 이전 키 하나에 새 키마다, 병합은 이전 키마다 새 키 하나로 한 행씩이다.
 */
@Table("tag_key_lineage")
record LineageRow(TagKind tagKind, String fromKey, String toKey, LineageKind kind) {

	@PersistenceCreator
	LineageRow {
		Objects.requireNonNull(tagKind, "tagKind");
		Objects.requireNonNull(fromKey, "fromKey");
		Objects.requireNonNull(toKey, "toKey");
		Objects.requireNonNull(kind, "kind");
	}

}
