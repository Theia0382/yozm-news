package net.yozmnews.platform;

import java.util.List;

/**
 * 일괄 조작의 결과 — docs/specs/platform.md §3.
 *
 * @param <T> 발행 하나의 결과
 */
public sealed interface BatchResult<T> {

	/**
	 * 처리했다.
	 *
	 * @param results 발행마다의 결과 — 요청 ID 의 UUID 순
	 * @param <T> 발행 하나의 결과
	 */
	record Processed<T>(List<T> results) implements BatchResult<T> {

		public Processed {
			results = List.copyOf(results);
		}

	}

	/**
	 * 요청이 한 번에 받는 발행 수의 상한을 넘어 아무것도 하지 않았다.
	 *
	 * @param limit 한 번에 받는 발행 수의 상한
	 * @param <T> 발행 하나의 결과
	 */
	record OverLimit<T>(int limit) implements BatchResult<T> {
	}

}
