package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.user.SignUpPolicyChangeResult;
import org.jspecify.annotations.Nullable;

/**
 * 가입 정책 변경의 결과 — docs/specs/admin.md §3 · admin-R1.
 *
 * @param result 바꿈 · 같은 값이라 바꾸지 않음
 * @param previousOpen 바꾸기 전 — 바꿨을 때만
 * @param open 지금 값
 */
record SignUpPolicyChangeResponse(Outcome result, @Nullable Boolean previousOpen, boolean open) {

	static SignUpPolicyChangeResponse of(SignUpPolicyChangeResult result) {
		return switch (result) {
			case SignUpPolicyChangeResult.Changed changed -> new SignUpPolicyChangeResponse(Outcome.CHANGED,
					changed.previous(), changed.current());
			case SignUpPolicyChangeResult.Unchanged unchanged -> new SignUpPolicyChangeResponse(Outcome.UNCHANGED, null,
					unchanged.current());
		};
	}

	enum Outcome {

		/** 바꿨다 */
		CHANGED,

		/** 지금 값과 같아 바꾸지 않았다 */
		UNCHANGED

	}

}
