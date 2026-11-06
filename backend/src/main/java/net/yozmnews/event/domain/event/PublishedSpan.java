package net.yozmnews.event.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 사건에 남은 배정의 발행 시각 범위 — 사건의 첫 · 마지막 기사 시각을 다시 맞추는 입력.
 *
 * @param first 가장 이른 발행 시각
 * @param last 가장 늦은 발행 시각
 */
public record PublishedSpan(Instant first, Instant last) {

	public PublishedSpan {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(last, "last");
	}

}
