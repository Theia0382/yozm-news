package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyChange;
import org.jspecify.annotations.Nullable;

/**
 * 가입 정책 이력의 한 행 — docs/specs/admin.md §3
 *
 * @param previousOpen 바꾸기 전 — 처음 값이면 null
 * @param open 바꾼 뒤
 * @param actor 정한 행위자의 종류
 * @param changedAt 바꾼 시각
 */
record SignUpPolicyHistoryResponse(@Nullable Boolean previousOpen, boolean open, ChangeActor actor,
		Instant changedAt) {

	static SignUpPolicyHistoryResponse of(SignUpPolicyChange change) {
		return new SignUpPolicyHistoryResponse(change.previousOpen(), change.open(), change.actor(), change.changedAt());
	}

}
