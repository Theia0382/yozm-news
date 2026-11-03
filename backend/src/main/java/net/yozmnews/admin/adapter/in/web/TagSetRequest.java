package net.yozmnews.admin.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 운영자 태그 체계 원문과 재평가 기간(일) — 기간이 없으면 0 이다(재평가하지 않는다). docs/specs/admin.md §3
 */
record TagSetRequest(@NotNull String text, @Nullable Integer recentDays) {

	/** 재평가 기간 — 없으면 0 */
	int days() {
		return (this.recentDays != null) ? this.recentDays : 0;
	}

}
