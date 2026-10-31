package net.yozmnews.source;

import java.util.List;
import java.util.Objects;

/**
 * 등록 전 운영자 원문의 피드 하나를 미리본 결과 — docs/specs/source.md §3
 */
public sealed interface ProfilePreviewResult {

	/** 원문이 검증에 걸려 받지 않았다 */
	record InvalidProfile(List<ProfileProblemView> problems) implements ProfilePreviewResult {

		public InvalidProfile {
			problems = List.copyOf(problems);
		}

	}

	/** 원문에 그 피드 식별자가 없어 받지 않았다(식별자 형식이 틀린 경우 포함) */
	record FeedNotInProfile(String feedKey) implements ProfilePreviewResult {

		public FeedNotInProfile {
			Objects.requireNonNull(feedKey, "feedKey");
		}

	}

	/** 받아 판정했고 활성화 기준을 넘었다 */
	record Passed(PreviewReport preview) implements ProfilePreviewResult {

		public Passed {
			Objects.requireNonNull(preview, "preview");
		}

	}

	/** 받아 판정했고 활성화 기준에 못 미쳤다 — 못 미친 기준은 비어 있지 않다 */
	record BelowCriteria(PreviewReport preview, List<CriterionShortfall> shortfalls) implements ProfilePreviewResult {

		public BelowCriteria {
			Objects.requireNonNull(preview, "preview");
			shortfalls = List.copyOf(shortfalls);
		}

	}

	/** 피드를 받지 못했다 */
	record Unfetched(FetchFailureView failure) implements ProfilePreviewResult {

		public Unfetched {
			Objects.requireNonNull(failure, "failure");
		}

	}

}
