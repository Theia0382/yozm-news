package net.yozmnews.user.domain.interest;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;

/**
 * 관심 설정 기본값의 정본 — 계정을 만들 때 설정 행에 넣는 값 — docs/specs/user.md §5 · user-R43
 * <p>
 * 주제 구독은 없고 형식 기본 제외는 체계 데이터에서 온다 — 여기에는 설정 행의 값만 있다.
 */
public final class InterestDefaults {

	/** 시간대 */
	public static final ZoneId TIMEZONE = ZoneId.of("Asia/Seoul");

	/** 다이제스트 생성 시각 */
	public static final LocalTime DIGEST_TIME = LocalTime.of(7, 0);

	/** 기본 일정 — 시간대 · 생성 시각. 설정 행이 없는 계정의 일정이기도 하다(일정 목록 · 사용자 시간대 — user-R60 · user-R61) */
	public static final DigestSchedule SCHEDULE = new DigestSchedule(TIMEZONE, DIGEST_TIME);

	/** 주간 변화 리포트를 받는가 */
	public static final boolean WEEKLY_REPORT = true;

	/** 주간 변화 리포트의 요일 */
	public static final DayOfWeek WEEKLY_REPORT_DAY = DayOfWeek.MONDAY;

	/** 월간 변화 리포트를 받는가 */
	public static final boolean MONTHLY_REPORT = true;

	/** 다이제스트 최대 후보 수의 단계 */
	public static final CandidateVolume CANDIDATE_VOLUME = CandidateVolume.NORMAL;

	/** 다이제스트 큰 슬롯 수 — 한 공급자 모델로 사용자 넷의 7일치 다이제스트를 만들어 재고 정했다 */
	public static final int BIG_SLOTS = 3;

	/** 다이제스트 요약 길이 */
	public static final SummaryLength SUMMARY_LENGTH = SummaryLength.NORMAL;

	/** 다이제스트 생성의 추론 강도 — 사용자 비용이 드는 기본값이다(측정 전 — ADR-0218) */
	public static final ReasoningEffort REASONING_EFFORT = ReasoningEffort.MEDIUM;

	private InterestDefaults() {
	}

}
