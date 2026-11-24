package net.yozmnews.account.domain.humancheck;

import java.util.Objects;

/**
 * 사람 확인의 결과 — 통과 · 거부 · 검증 서비스에 닿지 못함. docs/specs/account.md §4 · account-R33
 * <p>
 * 까닭은 고정 문구 · 오류 코드뿐이다 — 토큰 · 비밀 키를 담지 않는다.
 */
public sealed interface HumanCheckResult {

	/** 사람으로 확인됐다 */
	record Passed() implements HumanCheckResult {

	}

	/**
	 * 확인되지 않았다 — 요청을 거부한다.
	 * @param reason 까닭(고정 문구 · 오류 코드)
	 */
	record Rejected(String reason) implements HumanCheckResult {

		public Rejected {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 검증 서비스에 닿지 못했다(연결 · 마감 실패, 5xx, 읽지 못하는 본문) — 요청을 통과시키고 경고를 남긴다.
	 * @param reason 까닭(고정 문구)
	 */
	record Unavailable(String reason) implements HumanCheckResult {

		public Unavailable {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
