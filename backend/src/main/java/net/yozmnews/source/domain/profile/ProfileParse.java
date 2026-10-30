package net.yozmnews.source.domain.profile;

import java.util.List;
import java.util.Objects;

/**
 * 프로필 텍스트를 해석한 결과
 */
public sealed interface ProfileParse {

	/** 검증을 통과했다 */
	record Parsed(CollectionProfile profile) implements ProfileParse {

		public Parsed {
			Objects.requireNonNull(profile, "profile");
		}

	}

	/** 문제가 있다 — 한 번에 모은 문제 목록이고 비어 있지 않다 */
	record Invalid(List<ProfileProblem> problems) implements ProfileParse {

		public Invalid {
			problems = List.copyOf(problems);
			if (problems.isEmpty()) {
				throw new IllegalArgumentException("문제가 없는 Invalid");
			}
		}

	}

}
