package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

/**
 * 실패 지연을 거는 대조의 결과 — 맞음 · 틀림 · 대기. docs/specs/user.md §4 · user-R18
 */
sealed interface Throttled<T> {

	/** 맞았다 — 실패 기록을 지웠다 */
	record Passed<T>(T value) implements Throttled<T> {

		public Passed {
			Objects.requireNonNull(value, "value");
		}

	}

	/** 틀렸다 — 실패 기록을 올렸다 */
	record Failed<T>() implements Throttled<T> {

	}

	/** 연속 실패로 기다려야 한다 — 대조하지 않았다 */
	record Wait<T>(Instant retryAt) implements Throttled<T> {

		public Wait {
			Objects.requireNonNull(retryAt, "retryAt");
		}

	}

}
