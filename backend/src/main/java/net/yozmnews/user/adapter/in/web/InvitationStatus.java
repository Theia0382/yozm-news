package net.yozmnews.user.adapter.in.web;

import java.util.Optional;

import net.yozmnews.user.InvitationState;

/**
 * 링크를 연 사람에게 알리는 초대 상태 — 초대의 상태에 "읽을 수 없거나 없는 토큰"을 더한다. docs/specs/user.md §3
 */
enum InvitationStatus {

	/** 가입에 쓸 수 있다 */
	VALID,

	/** 이미 가입에 썼다 */
	USED,

	/** 만료됐다 */
	EXPIRED,

	/** 운영자가 취소했다 */
	REVOKED,

	/** 읽을 수 없거나 없는 토큰이다 */
	UNKNOWN;

	static InvitationStatus of(Optional<InvitationState> state) {
		return state.map(found -> switch (found) {
			case VALID -> VALID;
			case USED -> USED;
			case EXPIRED -> EXPIRED;
			case REVOKED -> REVOKED;
		}).orElse(UNKNOWN);
	}

}
