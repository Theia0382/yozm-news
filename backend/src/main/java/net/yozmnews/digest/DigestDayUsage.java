package net.yozmnews.digest;

/**
 * 날짜 하나의 사용량 — 그 날짜의 모든 시도(덮인 시도 · 실패 포함). docs/specs/digest.md §3 · digest-R38
 *
 * @param attempts 그 날짜의 다이제스트 행 수
 * @param inputTokens 토큰이 있는 행의 입력 토큰 합
 * @param outputTokens 토큰이 있는 행의 출력 토큰 합
 */
public record DigestDayUsage(int attempts, long inputTokens, long outputTokens) {

	public DigestDayUsage {
		if (attempts < 1 || inputTokens < 0 || outputTokens < 0) {
			throw new IllegalArgumentException(
					"시도는 1 이상 · 토큰은 0 이상이다: " + attempts + " · " + inputTokens + " · " + outputTokens);
		}
	}

}
