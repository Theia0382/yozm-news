package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 운영자 목록의 초대 하나 — 토큰은 싣지 않는다. docs/specs/user.md §3
 *
 * @param id 초대 ID
 * @param memo 메모 — 없으면 null
 * @param createdAt 발급 시각
 * @param expiresAt 만료 시각
 * @param state 조회 때의 상태
 * @param usedAt 쓴 시각 — 쓰지 않았으면 null
 * @param usedByEmail 가입한 계정의 이메일 — 쓰지 않았거나 그 계정이 지워졌으면 null
 * @param revokedAt 취소한 시각 — 취소하지 않았으면 null
 */
public record InvitationView(long id, @Nullable String memo, Instant createdAt, Instant expiresAt,
		InvitationState state, @Nullable Instant usedAt, @Nullable String usedByEmail, @Nullable Instant revokedAt) {

	public InvitationView {
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
		Objects.requireNonNull(state, "state");
	}

}
