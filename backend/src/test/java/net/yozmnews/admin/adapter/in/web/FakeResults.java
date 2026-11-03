package net.yozmnews.admin.adapter.in.web;

import org.jspecify.annotations.Nullable;

/** 시험 도우미 — 운영자 API 슬라이스 대역의 정해 둔 결과 */
final class FakeResults {

	private FakeResults() {
	}

	/** 시험이 정한 결과 — 정하지 않았으면 실패한다 */
	static <T> T given(@Nullable T result) {
		if (result == null) {
			throw new IllegalStateException("시험이 결과를 정하지 않았다");
		}
		return result;
	}

}
