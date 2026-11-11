package net.yozmnews.tag.domain.tagset;

import java.util.List;
import java.util.Objects;

/**
 * 태그 체계 원문을 해석한 결과 — docs/specs/tag.md §4
 */
public sealed interface TagSetParse {

	/** 검증을 통과했다 */
	record Parsed(TagSetDefinition definition) implements TagSetParse {

		public Parsed {
			Objects.requireNonNull(definition, "definition");
		}

	}

	/** 문제가 있다 — 찾은 순서대로 모두 */
	record Invalid(List<TagSetProblem> problems) implements TagSetParse {

		public Invalid {
			problems = List.copyOf(problems);
			if (problems.isEmpty()) {
				throw new IllegalArgumentException("문제가 없는 실패는 없다");
			}
		}

	}

}
