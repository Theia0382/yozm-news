package net.yozmnews.digest.domain.input;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;

/**
 * 프롬프트의 사용자 프로필 재료 — 후보 선별이 관심 설정에서 만든다 — docs/specs/digest.md §5 · digest-R22
 *
 * @param topics 구독 행의 이름 — 활성 태그 체계의 이름, 체계 순서
 * @param excludedRegions 제외한 지역의 이름 — 체계 순서
 * @param timezone 사용자 시간대 — 후보의 발행 시각을 이 시간대로 적는다
 * @param summaryLength 요약 길이 — 지시의 길이 기준을 고른다
 * @param effort 생성의 추론 강도
 */
public record DigestProfile(List<String> topics, List<String> excludedRegions, ZoneId timezone,
		SummaryLength summaryLength, ReasoningEffort effort) {

	public DigestProfile {
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		excludedRegions = List.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(summaryLength, "summaryLength");
		Objects.requireNonNull(effort, "effort");
	}

}
