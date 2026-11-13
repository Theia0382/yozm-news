package net.yozmnews.user;

import java.util.Objects;

/**
 * 초대 취소의 결과 — 취소 · 취소할 수 없음. docs/specs/user.md §3
 */
public sealed interface InvitationRevocationResult {

	/** 취소했다 */
	record Revoked() implements InvitationRevocationResult {

	}

	/**
	 * 유효한 초대가 아니라 취소하지 않았다.
	 * @param state 지금 상태 — 쓴 것 · 만료한 것 · 이미 취소한 것
	 */
	record NotRevocable(InvitationState state) implements InvitationRevocationResult {

		public NotRevocable {
			Objects.requireNonNull(state, "state");
		}

	}

}
