package net.yozmnews.user.adapter.in.web;

import java.util.List;

/**
 * 제외 소스 저장 결과 — 판정 결과 200. docs/specs/user.md §3 · user-R49
 *
 * @param result 저장
 * @param excluded 저장한 뒤 고를 소스 안의 제외 소스 ID — 오름차순
 */
record InterestSourcesSaveResponse(Result result, List<Long> excluded) {

	/** 저장 결과의 판별자 */
	enum Result {

		/** 저장했다 */
		SAVED

	}

}
