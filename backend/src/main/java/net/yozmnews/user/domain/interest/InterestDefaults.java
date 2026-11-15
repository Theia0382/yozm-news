package net.yozmnews.user.domain.interest;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

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

	/** 주간 변화 리포트를 받는가 */
	public static final boolean WEEKLY_REPORT = true;

	/** 주간 변화 리포트의 요일 */
	public static final DayOfWeek WEEKLY_REPORT_DAY = DayOfWeek.MONDAY;

	/** 월간 변화 리포트를 받는가 */
	public static final boolean MONTHLY_REPORT = true;

	private InterestDefaults() {
	}

}
