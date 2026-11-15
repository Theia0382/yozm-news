package net.yozmnews.llm.domain.catalog;

import java.util.List;
import java.util.Objects;

/**
 * 허용 모델 목록 파일을 읽은 결과 — docs/specs/llm.md §4 · llm-R2
 */
public sealed interface ModelCatalogParse {

	/** 형식 규칙을 모두 통과했다 */
	record Parsed(ModelCatalog catalog) implements ModelCatalogParse {

		public Parsed {
			Objects.requireNonNull(catalog, "catalog");
		}

	}

	/** 문제가 하나 이상 있다 — 원문 순서대로 모두 */
	record Invalid(List<ModelCatalogProblem> problems) implements ModelCatalogParse {

		public Invalid {
			problems = List.copyOf(problems);
		}

	}

}
