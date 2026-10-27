package net.yozmnews.source.domain.profile;

import java.util.List;
import java.util.Objects;

/**
 * 프리셋 파일을 해석한 결과.
 */
public sealed interface PresetParse {

	record Parsed(Preset preset) implements PresetParse {

		public Parsed {
			Objects.requireNonNull(preset, "preset");
		}

	}

	/** 문제가 있다 — 비어 있지 않다 */
	record Invalid(List<ProfileProblem> problems) implements PresetParse {

		public Invalid {
			problems = List.copyOf(problems);
			if (problems.isEmpty()) {
				throw new IllegalArgumentException("문제가 없는 Invalid");
			}
		}

	}

}
