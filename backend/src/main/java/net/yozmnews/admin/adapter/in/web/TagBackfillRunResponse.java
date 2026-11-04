package net.yozmnews.admin.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillState;
import net.yozmnews.evaluation.TagBackfillVersionView;
import org.jspecify.annotations.Nullable;

/**
 * 빠진 태그 채움 하나 — 공개 보기를 옮기고 버전 ID 를 JSON 수로 낸다(admin-R1). docs/specs/admin.md §3
 */
record TagBackfillRunResponse(long id, int days, Instant from, Instant to, Instant requestedAt, TagBackfillState state,
		@Nullable Instant startedAt, @Nullable Instant finishedAt, long evaluations, long processed, long tagsAdded,
		long skippedOtherEvaluator, long skippedUnreadable, List<VersionItem> versions) {

	static TagBackfillRunResponse of(TagBackfillRunView run) {
		return new TagBackfillRunResponse(run.id(), run.days(), run.from(), run.to(), run.requestedAt(), run.state(),
				run.startedAt(), run.finishedAt(), run.evaluations(), run.processed(), run.tagsAdded(),
				run.skippedOtherEvaluator(), run.skippedUnreadable(),
				run.versions().stream().map(VersionItem::of).toList());
	}

	/** 버전 하나 — 채울 저장 기준과 결과 */
	record VersionItem(long id, int number, BigDecimal topicStoreThreshold, BigDecimal regionStoreThreshold,
			long evaluations, long processed, long tagsAdded, long skippedOtherEvaluator, long skippedUnreadable) {

		static VersionItem of(TagBackfillVersionView version) {
			return new VersionItem(version.version().value(), version.number(), version.topicStoreThreshold(),
					version.regionStoreThreshold(), version.evaluations(), version.processed(), version.tagsAdded(),
					version.skippedOtherEvaluator(), version.skippedUnreadable());
		}

	}

}
