package net.yozmnews.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import net.yozmnews.platform.UserId;

/**
 * 다이제스트가 실패가 아닌 끝(내용 있음 · 후보 없음)으로 끝났다 — 다이제스트가 끝낸 행을 쓰는 트랜잭션에서 내고, 이 모듈이 받아 그 사용자의
 * 사용자 스토리라인 정리 작업을 등록한다. docs/specs/user.md §3
 * <p>
 * 받는 쪽인 이 모듈이 선언한다 — 다이제스트는 이 모듈의 공개 조회를 읽어 이 모듈에 기대므로, 다이제스트가 선언한 이벤트를 이 모듈이 받으면 모듈이
 * 서로 기대게 된다({@link DigestFailed} 와 같은 꼴).
 *
 * @param user 다이제스트의 사용자
 * @param digestId 끝난 다이제스트 — 같은 다이제스트의 정리 작업은 하나다
 * @param digestDate 다이제스트 날짜
 * @param completedAt 끝낸 시각
 */
public record DigestCompleted(UserId user, long digestId, LocalDate digestDate, Instant completedAt) {

	public DigestCompleted {
		Objects.requireNonNull(user, "user");
		if (digestId < 1) {
			throw new IllegalArgumentException("다이제스트 식별자는 1 이상이어야 한다: " + digestId);
		}
		Objects.requireNonNull(digestDate, "digestDate");
		Objects.requireNonNull(completedAt, "completedAt");
	}

}
