package net.yozmnews.user;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 계정이 생겼다 — 계정을 만드는 정본이 계정을 쓴 트랜잭션에서 낸다. 받는 쪽은 커밋 뒤 받아 관심 설정 기본값을 쓴다. docs/specs/account.md §3
 *
 * @param user 만든 계정
 * @param createdAt 계정을 만든 시각 — 계정 행의 만든 시각과 같다
 */
public record AccountCreated(UserId user, Instant createdAt) {

	public AccountCreated {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(createdAt, "createdAt");
	}

}
