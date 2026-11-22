package net.yozmnews.user.domain.summary;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * 사용자 스토리라인 하나의 시도 기록 — 공급자에 보낸 정리 호출의 수와 토큰 합, 연속 실패. 정리 행이 없어도 있다. docs/specs/user.md §4 · §6
 *
 * @param userStorylineId 사용자 스토리라인 ID
 * @param calls 공급자에 보낸 호출 수 — 저장한 것 · 버린 것 · 실패한 것 모두
 * @param totalInputTokens 공급자가 토큰을 알려 준 호출의 입력 토큰 합
 * @param totalOutputTokens 공급자가 토큰을 알려 준 호출의 출력 토큰 합
 * @param failures 연속 실패 수 — 저장에 성공하면 0
 * @param failureCode 마지막 실패의 코드 — 연속 실패가 0 이면 null
 * @param failedAt 마지막 실패 시각 — 연속 실패가 0 이면 null
 */
public record SummaryAttempt(long userStorylineId, int calls, long totalInputTokens, long totalOutputTokens,
		int failures, @Nullable String failureCode, @Nullable Instant failedAt) {

	public SummaryAttempt {
		if (calls < 1 || totalInputTokens < 0 || totalOutputTokens < 0 || failures < 0) {
			throw new IllegalArgumentException("시도 기록의 수가 범위 밖이다");
		}
		if ((failures == 0) != (failureCode == null) || (failures == 0) != (failedAt == null)) {
			throw new IllegalArgumentException("연속 실패 수와 마지막 실패의 코드 · 시각은 함께 있거나 함께 없다");
		}
	}

}
