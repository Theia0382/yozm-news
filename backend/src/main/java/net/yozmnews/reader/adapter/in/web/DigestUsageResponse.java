package net.yozmnews.reader.adapter.in.web;

import net.yozmnews.digest.DigestDayUsage;

/**
 * 날짜 하나의 사용량 — 그 날짜 모든 시도(덮인 시도 · 실패 포함). docs/specs/reader.md §5 · reader-R9
 *
 * @param attempts 시도 수
 * @param inputTokens 공급자가 답한 시도의 입력 토큰 합
 * @param outputTokens 공급자가 답한 시도의 출력 토큰 합
 */
record DigestUsageResponse(int attempts, long inputTokens, long outputTokens) {

	static DigestUsageResponse of(DigestDayUsage usage) {
		return new DigestUsageResponse(usage.attempts(), usage.inputTokens(), usage.outputTokens());
	}

}
