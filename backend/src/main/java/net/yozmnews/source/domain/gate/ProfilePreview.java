package net.yozmnews.source.domain.gate;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.ProfileProblem;

/**
 * 등록 전 운영자 프로필 원문의 피드 하나를 미리본 결과 — docs/specs/source.md §4 · source-R9
 */
public sealed interface ProfilePreview {

	/** 원문이 검증에 걸려 피드를 받지 않았다 */
	record Invalid(List<ProfileProblem> problems) implements ProfilePreview {

		public Invalid {
			problems = List.copyOf(problems);
		}

	}

	/** 원문에 그 피드 식별자가 없어 피드를 받지 않았다 */
	record FeedNotInProfile(FeedKey key) implements ProfilePreview {

		public FeedNotInProfile {
			Objects.requireNonNull(key, "key");
		}

	}

	/** 피드를 받아 판정했다 */
	record Previewed(PreviewOutcome outcome) implements ProfilePreview {

		public Previewed {
			Objects.requireNonNull(outcome, "outcome");
		}

	}

}
