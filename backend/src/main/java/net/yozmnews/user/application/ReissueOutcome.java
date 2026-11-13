package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.user.domain.secret.RecoveryCode;

/**
 * 복구 코드 재발급의 결과 — 재발급 · 거절 · 대기. docs/specs/user.md §4
 */
public sealed interface ReissueOutcome {

	/** 새 코드를 줬다 — 앞 코드는 무효가 됐다 */
	record Reissued(RecoveryCode recoveryCode) implements ReissueOutcome {

		public Reissued {
			Objects.requireNonNull(recoveryCode, "recoveryCode");
		}

	}

	/** 현재 비밀번호가 틀렸다 */
	record Rejected() implements ReissueOutcome {

	}

	/** 연속 실패로 기다려야 한다 — 대조하지 않았다 */
	record Wait(Instant retryAt) implements ReissueOutcome {

		public Wait {
			Objects.requireNonNull(retryAt, "retryAt");
		}

	}

}
