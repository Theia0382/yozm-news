package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.ChangeActor;
import org.jspecify.annotations.Nullable;

/**
 * 가입 정책 이력의 한 행 — docs/specs/user.md §3
 *
 * @param previousOpen 바꾸기 전 값 — 처음 값이면 null
 * @param open 바꾼 뒤 값
 * @param actor 정한 행위자의 종류
 * @param changedAt 바꾼 시각
 */
public record SignUpPolicyChange(@Nullable Boolean previousOpen, boolean open, ChangeActor actor,
		Instant changedAt) {

	public SignUpPolicyChange {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(changedAt, "changedAt");
	}

}
