package net.yozmnews.user;

import java.io.Serial;

/**
 * 없는 초대 — 운영자 취소가 받은 ID 의 초대가 없다. docs/specs/user.md §3
 */
public final class UnknownInvitationException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final long invitationId;

	public UnknownInvitationException(long invitationId) {
		super("없는 초대: " + invitationId);
		this.invitationId = invitationId;
	}

	public long invitationId() {
		return this.invitationId;
	}

}
