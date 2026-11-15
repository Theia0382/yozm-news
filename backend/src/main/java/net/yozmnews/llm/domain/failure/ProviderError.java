package net.yozmnews.llm.domain.failure;

import org.jspecify.annotations.Nullable;

/**
 * 공급자 오류에서 분류에 쓰려고 읽은 것 — docs/specs/llm.md §4 · llm-R8.
 * <p>
 * 오류의 문구는 담지 않는다 — 공급자가 문구에 키 조각을 되돌려준다(llm-R9).
 *
 * @param status HTTP 상태 코드
 * @param code 오류의 {@code code} — 없으면 null
 * @param type 오류의 {@code type} — 없으면 null
 * @param retryAfter 응답에 {@code retry-after} 헤더가 있는가
 */
public record ProviderError(int status, @Nullable String code, @Nullable String type, boolean retryAfter) {

	/** 시간이 지나면 풀릴 수 있는 상태인가 — 요청 제한(429)을 뺀 408 · 409 · 500 ~ 599. 두 공급자의 분류가 함께 읽는다 */
	boolean retriableStatus() {
		return this.status == 408 || this.status == 409 || (this.status >= 500 && this.status <= 599);
	}

}
