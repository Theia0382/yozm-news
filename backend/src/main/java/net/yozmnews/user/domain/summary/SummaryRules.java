package net.yozmnews.user.domain.summary;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.ReasoningEffort;

/**
 * 정리의 상수와 쉬는 기간의 정본 — 값은 모두 측정 전 목표다. docs/specs/user.md §5
 */
public final class SummaryRules {

	/** 한 호출에 넣는 사건의 상한 */
	public static final int MAX_EVENTS = 60;

	/** 한 실행(사용자 하나)이 보내는 호출의 상한 */
	public static final int MAX_CALLS_PER_RUN = 20;

	/** 지시가 권하는 글 길이(코드 포인트) — 제목 */
	public static final int TITLE_LENGTH = 60;

	/** 지시가 권하는 글 길이(코드 포인트) — 흐름 요약 */
	public static final int SUMMARY_LENGTH = 600;

	/** 지시가 권하는 글 길이(코드 포인트) — 최근 전개 */
	public static final int RECENT_LENGTH = 400;

	/** 출력 서술의 상한(코드 포인트) — 넘으면 상한까지만 남긴다. 권하는 길이의 두 배 */
	public static final int MAX_TITLE = TITLE_LENGTH * 2;

	/** 출력 서술의 상한(코드 포인트) — 흐름 요약 */
	public static final int MAX_SUMMARY = SUMMARY_LENGTH * 2;

	/** 출력 서술의 상한(코드 포인트) — 최근 전개 */
	public static final int MAX_RECENT = RECENT_LENGTH * 2;

	/** 출력의 글 한 벌(사용자 스토리라인 전체 또는 꼭대기 하나)에 주는 본문 토큰 — 서술의 상한 합에 JSON 몫을 더한 값 */
	public static final int MAX_BODY_TOKENS_PER_PART = 2_400;

	/** 정리 호출의 추론 강도 — 주어진 사건 줄을 간추리는 일이라 낮음으로 고정한다 */
	public static final ReasoningEffort EFFORT = ReasoningEffort.LOW;

	/** 연속 실패로 쉬는 날의 상한 */
	public static final int MAX_REST_DAYS = 7;

	private SummaryRules() {
	}

	/**
	 * 연속 실패 뒤 쉬는 끝 — 이 시각부터 다시 부른다.
	 * @param failures 연속 실패 수(1 이상)
	 * @param failedAt 마지막 실패 시각
	 * @return 마지막 실패 시각 + min(연속 실패 수, {@link #MAX_REST_DAYS})일
	 */
	public static Instant restUntil(int failures, Instant failedAt) {
		if (failures < 1) {
			throw new IllegalArgumentException("연속 실패가 없으면 쉬지 않는다: " + failures);
		}
		return failedAt.plus(Duration.ofDays(Math.min(failures, MAX_REST_DAYS)));
	}

	/**
	 * 한 호출의 출력 본문 토큰 상한 — 글 한 벌마다 {@link #MAX_BODY_TOKENS_PER_PART}, 관문의 상한을 넘지 않는다.
	 * @param tops 꼭대기 수(1 이상) — 하나면 글 한 벌, 둘 이상이면 꼭대기마다와 사용자 스토리라인 전체
	 */
	public static int bodyTokens(int tops) {
		if (tops < 1) {
			throw new IllegalArgumentException("꼭대기가 없다: " + tops);
		}
		int parts = (tops == 1) ? 1 : tops + 1;
		return (int) Math.min((long) MAX_BODY_TOKENS_PER_PART * parts, GenerationRequest.MAX_BODY_TOKENS);
	}

}
