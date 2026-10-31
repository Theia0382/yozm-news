package net.yozmnews.source.domain.gate;

import java.util.List;
import java.util.Objects;

import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.domain.fetch.FetchFailure;
import org.jspecify.annotations.Nullable;

/**
 * 피드 켜기의 결과 — docs/specs/source.md §4 · source-R5 · source-R9
 */
public sealed interface FeedActivation {

	/**
	 * 켰다.
	 * @param preview 꺼진 피드를 켜며 만든 미리보기 — 이미 켜져 있어 받지 않았으면 null
	 */
	record Activated(@Nullable FeedPreview preview) implements FeedActivation {
	}

	/** 설정 판정(프로필 · 피드 존재)으로 켜지 않았다 — 요청 전에도, 미리보기를 통과한 뒤 저장 직전에도 나온다 */
	record Refused(ActivationRefusal reason) implements FeedActivation {

		public Refused {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 미리보기가 기준에 못 미쳐 켜지 않았다.
	 * @param shortfalls 못 미친 기준 — 비어 있지 않다
	 */
	record BelowCriteria(FeedPreview preview, List<Shortfall> shortfalls) implements FeedActivation {

		/**
		 * 결과를 만든다.
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

	/** 피드를 받지 못해 켜지 않았다 */
	record Unfetched(FetchFailure failure) implements FeedActivation {

		public Unfetched {
			Objects.requireNonNull(failure, "failure");
		}

	}

	/** 미리본 뒤 저장 전에 그 피드의 읽기 설정(URL 포함)이 바뀌어 켜지 않았다 */
	record ReadingChanged() implements FeedActivation {
	}

}
