package net.yozmnews.digest;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.tag.TagSetId;

/**
 * 한 사용자의 최근 기간별 관심 — {@link InterestHistory#recent} 가 낸다. docs/specs/digest.md §3 · digest-R29 · digest-R31
 *
 * @param tagSet 주제 키의 활성 태그 체계 버전
 * @param zone 기간을 자른 사용자 시간대
 * @param span 보인 기간
 * @param periods 기간 — 시간 순, 마지막이 진행 중. 수는 {@code span.count()}
 * @param prominent 드러난 주제 — 보인 기간 가운데 한 번이라도 관심 x 의 크기가 "관심 있음"의 하한 이상인 중분류(관심 쪽 · 싫어요 쪽)
 */
public record InterestTimeline(TagSetId tagSet, ZoneId zone, InterestHistorySpan span,
		List<InterestPeriodView> periods, Set<String> prominent) {

	public InterestTimeline {
		Objects.requireNonNull(tagSet, "tagSet");
		Objects.requireNonNull(zone, "zone");
		Objects.requireNonNull(span, "span");
		periods = List.copyOf(Objects.requireNonNull(periods, "periods"));
		prominent = Set.copyOf(Objects.requireNonNull(prominent, "prominent"));
	}

}
