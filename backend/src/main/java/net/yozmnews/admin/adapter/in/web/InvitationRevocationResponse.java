package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.user.InvitationRevocationResult;
import net.yozmnews.user.InvitationState;
import org.jspecify.annotations.Nullable;

/**
 * 초대 취소의 결과 — docs/specs/admin.md §3 · admin-R1. 없는 초대는 404 다.
 *
 * @param result 취소 · 취소할 수 없음
 * @param state 취소할 수 없을 때 지금 상태 — 그 밖 null
 */
record InvitationRevocationResponse(Outcome result, @Nullable InvitationState state) {

	static InvitationRevocationResponse of(InvitationRevocationResult result) {
		return switch (result) {
			case InvitationRevocationResult.Revoked revoked -> new InvitationRevocationResponse(Outcome.REVOKED, null);
			case InvitationRevocationResult.NotRevocable not -> new InvitationRevocationResponse(Outcome.NOT_REVOCABLE,
					not.state());
		};
	}

	enum Outcome {

		/** 취소했다 */
		REVOKED,

		/** 유효한 초대가 아니라 취소하지 않았다 */
		NOT_REVOCABLE

	}

}
