package net.yozmnews.account;

/**
 * 가입 정책 변경의 결과 — docs/specs/account.md §3
 */
public sealed interface SignUpPolicyChangeResult {

	/** 바꿨다 */
	record Changed(boolean previous, boolean current) implements SignUpPolicyChangeResult {

	}

	/** 지금 값과 같아 바꾸지 않았다 */
	record Unchanged(boolean current) implements SignUpPolicyChangeResult {

	}

}
