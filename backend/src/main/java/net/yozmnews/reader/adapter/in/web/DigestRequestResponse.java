package net.yozmnews.reader.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.digest.DigestRequestOutcome;
import org.jspecify.annotations.Nullable;

/**
 * 수동 생성 요청의 결과 — 판정 결과 200. docs/specs/reader.md §5 · reader-R11
 *
 * @param result 받음 · 진행 중 · 오늘 횟수 소진 · 쓸 모델 없음
 * @param digestDate 받았을 때 만들 다이제스트의 날짜 — 그 밖에는 null
 * @param regenerated 받았을 때 그 날짜를 같은 기간으로 다시 만드는가 — 그 밖에는 null
 * @param resetsAt 오늘 횟수를 다 썼을 때 다시 차는 시각 — 그 밖에는 null
 */
record DigestRequestResponse(DigestRequestResult result, @Nullable LocalDate digestDate, @Nullable Boolean regenerated,
		@Nullable Instant resetsAt) {

	static DigestRequestResponse of(DigestRequestOutcome outcome) {
		return switch (outcome) {
			case DigestRequestOutcome.Requested requested -> new DigestRequestResponse(DigestRequestResult.REQUESTED,
					requested.digestDate(), requested.regenerated(), null);
			case DigestRequestOutcome.InProgress inProgress ->
				new DigestRequestResponse(DigestRequestResult.IN_PROGRESS, null, null, null);
			case DigestRequestOutcome.DailyLimitReached limit ->
				new DigestRequestResponse(DigestRequestResult.DAILY_LIMIT_REACHED, null, null, limit.resetsAt());
			case DigestRequestOutcome.NoUsableModel none ->
				new DigestRequestResponse(DigestRequestResult.NO_USABLE_MODEL, null, null, null);
		};
	}

	/** 수동 생성 요청의 판별자 */
	enum DigestRequestResult {

		/** 받았다 — 생성은 작업 서버가 한다 */
		REQUESTED,

		/** 다이제스트가 생성을 기다리거나 생성 중이다 */
		IN_PROGRESS,

		/** 오늘(사용자 시간대) 횟수를 다 썼다 */
		DAILY_LIMIT_REACHED,

		/** 쓸 모델이 없다 */
		NO_USABLE_MODEL

	}

}
