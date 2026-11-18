package net.yozmnews.digest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 수동 다이제스트 생성 요청의 결과 — {@link DigestRequests} 가 낸다. docs/specs/digest.md §3
 */
public sealed interface DigestRequestOutcome {

	/**
	 * 받았다 — 다이제스트 행을 만들고 생성 작업을 등록했다.
	 * @param digestDate 만들 다이제스트의 날짜
	 * @param regenerated 그 날짜의 다이제스트를 같은 기간으로 다시 만드는가 — 거짓이면 새 기간
	 */
	record Requested(LocalDate digestDate, boolean regenerated) implements DigestRequestOutcome {

		public Requested {
			Objects.requireNonNull(digestDate, "digestDate");
		}

	}

	/** 그 사용자의 다이제스트가 생성을 기다리거나 생성 중이다 — 받지 않았고 횟수에 세지 않는다 */
	record InProgress() implements DigestRequestOutcome {
	}

	/**
	 * 오늘(사용자 시간대) 받을 수 있는 횟수를 다 썼다.
	 * @param resetsAt 다시 받을 수 있는 시각 — 사용자 시간대의 다음 날 0시
	 */
	record DailyLimitReached(Instant resetsAt) implements DigestRequestOutcome {

		public DailyLimitReached {
			Objects.requireNonNull(resetsAt, "resetsAt");
		}

	}

	/** 쓸 모델이 없다(계정이 없거나 쓸 수 있는 계정이 아님 포함) — 받지 않았고 횟수에 세지 않는다 */
	record NoUsableModel() implements DigestRequestOutcome {
	}

}
