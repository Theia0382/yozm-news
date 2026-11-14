package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 로그인 시도의 결과 — 로그인됨 · 거절 · 대기. docs/specs/user.md §4 · user-R17
 */
public sealed interface SignInOutcome {

	/** 맞는 비밀번호의 쓰는 계정 */
	record SignedIn(UserId userId) implements SignInOutcome {

		public SignedIn {
			Objects.requireNonNull(userId, "userId");
		}

	}

	/** 계정이 없거나 쓰지 않거나 비밀번호가 틀림 — 셋을 가르지 않는다 */
	record Rejected() implements SignInOutcome {

	}

	/** 연속 실패로 기다려야 한다 — 비밀번호를 대조하지 않았다 */
	record Wait(Instant retryAt) implements SignInOutcome {

		public Wait {
			Objects.requireNonNull(retryAt, "retryAt");
		}

	}

}
