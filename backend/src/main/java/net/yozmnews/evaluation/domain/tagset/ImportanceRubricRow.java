package net.yozmnews.evaluation.domain.tagset;

import java.util.Objects;

import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 버전의 중요도 기준 한 행 — aggregate {@link TagSet} 의 자식 — docs/specs/evaluation.md §6
 */
@Table("importance_rubrics")
record ImportanceRubricRow(int level, String description) {

	@PersistenceCreator
	ImportanceRubricRow {
		Objects.requireNonNull(description, "description");
	}

}
