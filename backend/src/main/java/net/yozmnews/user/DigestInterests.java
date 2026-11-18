package net.yozmnews.user;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TagSetId;

/**
 * 다이제스트가 읽는 관심 설정 — {@link DigestInterestsDirectory} 가 낸다. 키는 모두 {@link #tagSet} 버전의 것이다. docs/specs/user.md §3
 *
 * @param tagSet 낸 키의 활성 태그 체계 버전 — 부르는 쪽이 기사 평가 키를 푼 계보 표의 버전과 맞춘다
 * @param topics 주제 구독 행 — 키 순
 * @param excludedRegions 제외한 지역 키
 * @param excludedFormats 제외한 형식 키
 * @param excludedSources 제외한 소스
 * @param interestText 자유 텍스트 관심사 — 앞뒤 공백 없음, 없으면 빈 문자열
 * @param timezone 사용자 시간대
 * @param maxCandidates 최대 후보 수 — 분량 설정의 단계 값 또는 직접 입력한 수
 * @param bigSlots 큰 슬롯 수 — 0 이면 큰 슬롯을 넣지 않는다
 * @param summaryLength 요약 길이
 * @param effort 다이제스트 생성의 추론 강도
 */
public record DigestInterests(TagSetId tagSet, List<TopicSubscription> topics, Set<String> excludedRegions,
		Set<String> excludedFormats, Set<SourceId> excludedSources, String interestText, ZoneId timezone,
		int maxCandidates, int bigSlots, SummaryLength summaryLength, ReasoningEffort effort) {

	public DigestInterests {
		Objects.requireNonNull(tagSet, "tagSet");
		topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
		excludedRegions = Set.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
		excludedFormats = Set.copyOf(Objects.requireNonNull(excludedFormats, "excludedFormats"));
		excludedSources = Set.copyOf(Objects.requireNonNull(excludedSources, "excludedSources"));
		Objects.requireNonNull(interestText, "interestText");
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(summaryLength, "summaryLength");
		Objects.requireNonNull(effort, "effort");
	}

}
