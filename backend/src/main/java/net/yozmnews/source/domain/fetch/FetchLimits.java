package net.yozmnews.source.domain.fetch;

import java.time.Duration;
import java.util.Objects;

/**
 * 한 번의 수집에 거는 제한 — 정본 · docs/specs/source.md §4 · source-R28.
 * <p>
 * 구현은 {@link #DEFAULT} 를 쓰고, 테스트는 같은 경로를 짧은 값으로 탄다.
 *
 * @param deadline 한 번의 수집 전체(풀 대기 · 연결 · 응답 · 리다이렉트 홉 · 본문)에 거는 마감
 * @param maxBodyBytes 본문 크기 상한(압축을 푼 바이트)
 */
public record FetchLimits(Duration deadline, long maxBodyBytes, int maxRedirects) {

	public static final Duration DEADLINE = Duration.ofSeconds(10);

	public static final long MAX_BODY_BYTES = 5L * 1024 * 1024;

	public static final int MAX_REDIRECTS = 5;

	public static final FetchLimits DEFAULT = new FetchLimits(DEADLINE, MAX_BODY_BYTES, MAX_REDIRECTS);

	/**
	 * 값을 확인한다.
	 * @throws IllegalArgumentException 마감이 0 이하, 크기 상한이 0 이하, 홉 수가 음수일 때
	 */
	public FetchLimits {
		Objects.requireNonNull(deadline, "deadline");
		if (!deadline.isPositive()) {
			throw new IllegalArgumentException("마감은 0 보다 길어야 한다: " + deadline);
		}
		if (maxBodyBytes <= 0) {
			throw new IllegalArgumentException("크기 상한은 0 보다 커야 한다: " + maxBodyBytes);
		}
		if (maxRedirects < 0) {
			throw new IllegalArgumentException("리다이렉트 상한은 0 이상이다: " + maxRedirects);
		}
	}

}
