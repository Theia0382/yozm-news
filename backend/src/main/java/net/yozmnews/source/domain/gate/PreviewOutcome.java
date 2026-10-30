package net.yozmnews.source.domain.gate;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.domain.fetch.FetchFailure;

/**
 * 미리보기의 판정
 */
public sealed interface PreviewOutcome {

	/** 기준을 넘었다 */
	record Passed(FeedPreview preview) implements PreviewOutcome {

		public Passed {
			Objects.requireNonNull(preview, "preview");
		}

	}

	/**
	 * 기준에 못 미쳤다.
	 * @param shortfalls 못 미친 기준 — 비어 있지 않다
	 */
	record BelowCriteria(FeedPreview preview, List<Shortfall> shortfalls) implements PreviewOutcome {

		/**
		 * 판정을 만든다.
		 * @throws IllegalArgumentException 못 미친 기준이 비었을 때
		 */
		public BelowCriteria {
			Objects.requireNonNull(preview, "preview");
			shortfalls = List.copyOf(shortfalls);
			if (shortfalls.isEmpty()) {
				throw new IllegalArgumentException("못 미친 기준이 없다");
			}
		}

	}

	/** 피드를 받지 못했다 */
	record Unfetched(FetchFailure failure) implements PreviewOutcome {

		public Unfetched {
			Objects.requireNonNull(failure, "failure");
		}

	}

}
