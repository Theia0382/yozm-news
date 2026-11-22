package net.yozmnews.user;

import java.util.Objects;

/**
 * 사용자 스토리라인 하나의 정리에 쓴 모델과 토큰 — 금액으로 바꾸지 않는다. docs/specs/user.md §3
 *
 * @param provider 마지막 정리의 공급자 이름
 * @param model 마지막 정리의 모델 ID
 * @param inputTokens 마지막 정리의 입력 토큰
 * @param outputTokens 마지막 정리의 출력 토큰
 * @param calls 지금까지 공급자에 보낸 호출 수 — 실패한 · 버린 호출 포함
 * @param totalInputTokens 공급자가 토큰을 알려 준 모든 호출의 입력 토큰 합
 * @param totalOutputTokens 공급자가 토큰을 알려 준 모든 호출의 출력 토큰 합
 */
public record SummaryUsage(String provider, String model, int inputTokens, int outputTokens, int calls,
		long totalInputTokens, long totalOutputTokens) {

	public SummaryUsage {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(model, "model");
		if (inputTokens < 0 || outputTokens < 0 || calls < 0 || totalInputTokens < 0 || totalOutputTokens < 0) {
			throw new IllegalArgumentException("사용량은 0 이상이다");
		}
	}

}
