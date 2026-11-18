package net.yozmnews.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 다이제스트가 실패로 끝났다 — 다이제스트가 실패를 쓰는 트랜잭션에서 내고, 이 모듈이 받아 그 사용자에게 알림을 남긴다. docs/specs/user.md §3
 * <p>
 * 받는 쪽인 이 모듈이 선언한다 — 다이제스트는 이 모듈의 공개 조회를 읽어 이 모듈에 기대므로, 다이제스트가 선언한 이벤트를 이 모듈이 받으면 모듈이
 * 서로 기대게 된다.
 *
 * @param user 다이제스트의 사용자
 * @param digestId 실패한 다이제스트 — 같은 다이제스트의 알림은 하나다
 * @param digestDate 다이제스트 날짜
 * @param cause 실패의 갈래
 * @param failedAt 실패로 끝낸 시각
 */
public record DigestFailed(UserId user, long digestId, LocalDate digestDate, DigestFailureCause cause,
		Instant failedAt) {

	public DigestFailed {
		Objects.requireNonNull(user, "user");
		if (digestId < 1) {
			throw new IllegalArgumentException("다이제스트 식별자는 1 이상이어야 한다: " + digestId);
		}
		Objects.requireNonNull(digestDate, "digestDate");
		Objects.requireNonNull(cause, "cause");
		Objects.requireNonNull(failedAt, "failedAt");
	}

}
