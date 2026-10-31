package net.yozmnews.source;

import org.jspecify.annotations.Nullable;

/**
 * 요약문 길이 분포(코드 포인트) — docs/specs/source.md §3.
 * <p>
 * 요약문이 있는 기사가 없으면 최소 · 아래 중앙값 · 최대는 null 이다.
 */
public record SummaryLengthsView(int present, int absent, @Nullable Integer min, @Nullable Integer median,
		@Nullable Integer max) {

}
