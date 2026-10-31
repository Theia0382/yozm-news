package net.yozmnews.source;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 피드 켜기의 결과 — docs/specs/source.md §3
 */
public sealed interface FeedEnableResult {

	/** 켰다 — 꺼진 피드를 켜며 만든 미리보기. 이미 켜져 있어 받지 않았으면 null */
	record Enabled(@Nullable PreviewReport preview) implements FeedEnableResult {
	}

	/** 설정 판정(프로필 · 피드 존재)으로 켜지 않았다 */
	record Refused(ActivationRefusal reason) implements FeedEnableResult {

		public Refused {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/** 미리보기가 활성화 기준에 못 미쳐 켜지 않았다 — 못 미친 기준은 비어 있지 않다 */
	record BelowCriteria(PreviewReport preview, List<CriterionShortfall> shortfalls) implements FeedEnableResult {

		public BelowCriteria {
			Objects.requireNonNull(preview, "preview");
			shortfalls = List.copyOf(shortfalls);
		}

	}

	/** 피드를 받지 못해 켜지 않았다 */
	record Unfetched(FetchFailureView failure) implements FeedEnableResult {

		public Unfetched {
			Objects.requireNonNull(failure, "failure");
		}

	}

	/** 미리본 뒤 저장 전에 그 피드의 읽기 설정이 바뀌어 켜지 않았다 */
	record ReadingChanged() implements FeedEnableResult {
	}

}
