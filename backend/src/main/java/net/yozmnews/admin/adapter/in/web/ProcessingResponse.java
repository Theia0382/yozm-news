package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.platform.RoundSummary;
import org.jspecify.annotations.Nullable;

/**
 * 처리 현황 — 회차마다 남은 수 · 미룬 수와 최근 1시간의 처리량 · 마지막 회차 · docs/specs/admin.md §3. 각 모듈의 처리 현황 조회 결과를 옮기기만
 * 한다.
 *
 * @param rounds 회차 — 사건(기사 표현 · 배정 · 재판정 · 병합) · 흐름(잇기 · 관계) · 태그 평가 순
 */
record ProcessingResponse(List<ProcessingRoundResponse> rounds) {

	static ProcessingResponse of(List<RoundQueue> rounds) {
		return new ProcessingResponse(rounds.stream().map(ProcessingRoundResponse::of).toList());
	}

	/**
	 * 회차 하나.
	 *
	 * @param round 회차 이름(예 {@code storyline-linking})
	 * @param waiting 회차가 처리할 남은 항목 — 점유 중 · 미룬 것 포함
	 * @param deferred 남은 항목 가운데 다시 볼 시각이 아직 오지 않은 것
	 * @param recentHandled 최근 1시간 안에 끝난 회차들이 처리한 수의 합
	 * @param recentRuns 최근 1시간 안에 끝난 회차 수
	 * @param lastRun 마지막으로 끝난 회차 — 기록이 없으면 null
	 */
	record ProcessingRoundResponse(String round, long waiting, long deferred, long recentHandled, int recentRuns,
			@Nullable ProcessingLastRunResponse lastRun) {

		static ProcessingRoundResponse of(RoundQueue queue) {
			RoundSummary recent = queue.recent();
			RoundSummary.@Nullable LastRound last = recent.last();
			return new ProcessingRoundResponse(queue.round(), queue.waiting(), queue.deferred(), recent.handled(),
					recent.runs(), (last == null) ? null : ProcessingLastRunResponse.of(last));
		}

	}

	/**
	 * 끝난 회차 하나.
	 *
	 * @param startedAt 시작 시각
	 * @param endedAt 끝난 시각
	 * @param batches 부른 뭉치 수
	 * @param handled 처리한 수
	 * @param endedBy 끝난 까닭
	 */
	record ProcessingLastRunResponse(Instant startedAt, Instant endedAt, int batches, int handled, RoundEnd endedBy) {

		static ProcessingLastRunResponse of(RoundSummary.LastRound last) {
			return new ProcessingLastRunResponse(last.startedAt(), last.endedAt(), last.batches(), last.handled(),
					last.end());
		}

	}

}
