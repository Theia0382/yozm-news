package net.yozmnews.user.domain.summary;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 사용자 스토리라인 하나의 정리 — 그 사용자의 LLM 키로 만든 글과 그 재료의 기록. docs/specs/user.md §4 · §6
 *
 * @param userStorylineId 사용자 스토리라인 ID
 * @param whole 흐름 전체의 글
 * @param tops 꼭대기(스토리라인 ID)마다의 글 — 이 키들이 정리 때의 추적 대상이다. 비지 않는다
 * @param events 정리에 넣은 사건 ID → 그때의 대표 기사 ID
 * @param provider 마지막 정리의 공급자 이름
 * @param model 마지막 정리의 모델 ID
 * @param inputTokens 마지막 정리의 입력 토큰
 * @param outputTokens 마지막 정리의 출력 토큰
 * @param createdAt 처음 정리한 시각
 * @param updatedAt 마지막으로 정리한 시각
 * @param version 저장 때 견주는 판 — 0 부터
 */
public record StorylineSummary(long userStorylineId, SummaryText whole, Map<Long, SummaryText> tops,
		Map<Long, Long> events, String provider, String model, int inputTokens, int outputTokens, Instant createdAt,
		Instant updatedAt, int version) {

	public StorylineSummary {
		Objects.requireNonNull(whole, "whole");
		tops = Map.copyOf(Objects.requireNonNull(tops, "tops"));
		events = Map.copyOf(Objects.requireNonNull(events, "events"));
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (tops.isEmpty()) {
			throw new IllegalArgumentException("정리에는 꼭대기의 글이 하나 이상 있어야 한다");
		}
		if (inputTokens < 0 || outputTokens < 0 || version < 0) {
			throw new IllegalArgumentException("토큰과 판은 0 이상이어야 한다");
		}
	}

}
