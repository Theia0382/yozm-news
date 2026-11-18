package net.yozmnews.llm.domain.retry;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * 공급자 응답의 {@code retry-after} 헤더를 읽는 정본 — docs/specs/llm.md llm-R20.
 * <p>
 * 0 이상의 정수 초만 읽는다. 날짜 꼴 · 음수 · 숫자가 아닌 값은 읽지 않은 것으로 둔다 — 그때는 정해진 간격을 쓴다.
 */
public final class RetryAfter {

	/** 초 단위로 읽을 수 있는 자릿수 — 넘으면 읽지 않는다(어차피 따르는 상한을 넘는다) */
	private static final Pattern SECONDS = Pattern.compile("\\d{1,9}");

	private RetryAfter() {
	}

	public static Optional<Duration> parse(@Nullable String header) {
		if (header == null) {
			return Optional.empty();
		}
		String value = header.strip();
		if (!SECONDS.matcher(value).matches()) {
			return Optional.empty();
		}
		return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
	}

}
