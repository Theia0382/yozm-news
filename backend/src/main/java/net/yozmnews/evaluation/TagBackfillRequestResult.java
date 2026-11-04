package net.yozmnews.evaluation;

import java.util.Objects;

/**
 * 빠진 태그 채움 요청의 결과 — docs/specs/evaluation.md §3
 */
public sealed interface TagBackfillRequestResult {

	/** 채움 하나를 기록했다 — 작업 역할이 시작 가능 시각 뒤에 실행한다 */
	record Requested(TagBackfillRunView run) implements TagBackfillRequestResult {

		public Requested {
			Objects.requireNonNull(run, "run");
		}

	}

	/** 끝나지 않은 채움이 있어 아무것도 쓰지 않았다 — 그 채움 */
	record AlreadyOpen(TagBackfillRunView run) implements TagBackfillRequestResult {

		public AlreadyOpen {
			Objects.requireNonNull(run, "run");
		}

	}

	/** 일수가 1 ~ {@link TagBackfillAdministration#MAX_DAYS} 밖이라 아무것도 하지 않았다 */
	record DaysOutOfRange() implements TagBackfillRequestResult {
	}

}
