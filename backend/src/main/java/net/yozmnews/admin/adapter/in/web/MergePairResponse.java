package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.event.MergePair;
import org.jspecify.annotations.Nullable;

/**
 * 병합 판정이 남긴 쌍 하나 — docs/specs/admin.md §5 admin-R7.
 *
 * @param survivor 두 사건이 지금 같은 사건이면 그 사건 ID, 아니면 null
 */
record MergePairResponse(long low, long high, double probability, @Nullable Long survivor) {

	static MergePairResponse of(MergePair pair) {
		return new MergePairResponse(pair.low().value(), pair.high().value(), pair.probability(),
				(pair.survivor() == null) ? null : pair.survivor().value());
	}

}
