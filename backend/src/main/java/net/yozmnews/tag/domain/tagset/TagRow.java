package net.yozmnews.tag.domain.tagset;

import java.util.Objects;

import net.yozmnews.tag.TagKind;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 버전의 태그 한 행 — aggregate {@link TagSet} 의 자식 — docs/specs/tag.md §6.
 *
 * @param parentKey 주제 중분류의 대분류 키 — 그 밖에는 null
 * @param sortOrder 파일에 적힌 순서
 */
@Table("tags")
record TagRow(TagKind kind, String key, @Nullable String parentKey, String name, String description,
		boolean excludedByDefault, int sortOrder) {

	@PersistenceCreator
	TagRow {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
	}

}
