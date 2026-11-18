package net.yozmnews.digest.domain.schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.LlmClientFactory;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 일정 규칙과 상수의 정본 — 날짜의 생성 시각과 일정이 만들 날짜. docs/specs/digest.md §5
 * <p>
 * 사용자가 정한 시각은 생성 시각이다 — 일정은 생성 시각이 지난 뒤 그 날짜의 다이제스트를 만든다. 값은 측정 전 목표다.
 */
public final class DigestScheduleRules {

	/** 생성 시각이 지난 뒤 따라잡아 만드는 창 — 지나면 그 날짜는 만들지 않는다 */
	public static final Duration CATCH_UP = Duration.ofHours(2);

	/** 등록한 생성 작업이 시작되기를 기다리는 시간 — 지나도 생성을 기다리면 새 토큰으로 다시 등록한다 */
	public static final Duration DISPATCH_WAIT = Duration.ofMinutes(15);

	/** 만든 다이제스트가 시작될 기한 — 지나도 생성을 기다리면 다시 등록하지 않고 끝나지 않은 실패로 끝낸다 */
	public static final Duration START_DEADLINE = Duration.ofHours(1);

	/**
	 * 생성 중인 다이제스트의 임대 — 시작한 지 이만큼 지나도 끝나지 않으면 멈춘 생성으로 보고 끝나지 않은 실패로 끝낸다. 생성 호출 둘(끝까지 쓴 응답을
	 * 읽지 못하면 한 번 다시 묻는다)의 가장 긴 시간과 선별 뒤 트랜잭션의 여유 10분이다
	 */
	public static final Duration RUN_LEASE = LlmClientFactory.LONGEST_GENERATION_CALL.multipliedBy(2)
		.plus(Duration.ofMinutes(10));

	/** 사용자가 하루(사용자 시간대)에 요청할 수 있는 수동 생성 수 */
	public static final int MANUAL_PER_DAY = 3;

	private DigestScheduleRules() {
	}

	/** 그 시간대에서 그 날짜가 시작하는 시각 — 서머타임으로 0시가 없는 날은 그날의 첫 시각이다(java.time 의 규칙) */
	public static Instant startOfDay(LocalDate date, ZoneId zone) {
		return date.atStartOfDay(zone).toInstant();
	}

	/**
	 * 날짜의 생성 시각 — 그 시간대에서 그 날짜 · 시각. 없는 시각(서머타임의 틈)은 틈의 길이만큼 뒤로, 두 번 있는 시각(겹침)은 앞 오프셋이다
	 * (java.time 의 규칙).
	 */
	public static Instant generationTime(LocalDate date, LocalTime time, ZoneId zone) {
		return ZonedDateTime.of(date, time, zone).toInstant();
	}

	/**
	 * 일정이 지금 만들 날짜 — 그 시간대에서 지금의 날짜와 그 전날 가운데 생성 시각 T 가 {@code T ≤ now < T + CATCH_UP} 이고 그 사용자의 가장 늦은
	 * 다이제스트 날짜보다 뒤인 것. 둘 다 맞으면 늦은 날짜다.
	 * @param latest 그 사용자의 가장 늦은 다이제스트 날짜(계기 · 상태 무관) — 없으면 null
	 */
	public static Optional<LocalDate> dueDate(Instant now, LocalTime time, ZoneId zone, @Nullable LocalDate latest) {
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(zone, "zone");
		LocalDate today = now.atZone(zone).toLocalDate();
		for (LocalDate date : new LocalDate[] { today, today.minusDays(1) }) {
			Instant at = generationTime(date, time, zone);
			boolean inWindow = !now.isBefore(at) && now.isBefore(at.plus(CATCH_UP));
			if (inWindow && makesDate(date, latest)) {
				return Optional.of(date);
			}
		}
		return Optional.empty();
	}

	/**
	 * 오늘 일정이 아직 오지 않은 생성 시각 — digest-R40. 그 시간대에서 지금의 날짜를 오늘로, 일정이 오늘을 만들 것(쓸 모델이 있고 그 사용자의 가장
	 * 늦은 다이제스트 날짜가 오늘보다 앞섬 — digest-R29 와 같은 재료)이고 지금이 오늘 생성 시각 T 전이면 T 다.
	 * <p>
	 * 일정 회차의 기간 확인(끝이 시작보다 뒤가 아니면 만들지 않음 — digest-R30)은 보지 않는다. 기간 끝은 언제나 그 행을 만든 시각 이전이라(일정은
	 * 생성 시각, 수동은 요청 시각) T 가 지금보다 뒤면 직전 덮인 끝보다도 뒤고 기간이 선다.
	 * @param latest 그 사용자의 가장 늦은 다이제스트 날짜(계기 · 상태 무관) — 없으면 null
	 * @param usableModel 쓸 모델이 있는가 — 없으면 일정은 만들지 않는다
	 */
	public static Optional<Instant> upcomingToday(Instant now, LocalTime time, ZoneId zone, @Nullable LocalDate latest,
			boolean usableModel) {
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(time, "time");
		Objects.requireNonNull(zone, "zone");
		LocalDate today = now.atZone(zone).toLocalDate();
		Instant at = generationTime(today, time, zone);
		return (usableModel && makesDate(today, latest) && now.isBefore(at)) ? Optional.of(at) : Optional.empty();
	}

	/** 일정이 그 날짜를 만들 수 있는가 — 그 사용자의 가장 늦은 다이제스트 날짜보다 뒤(digest-R29) */
	private static boolean makesDate(LocalDate date, @Nullable LocalDate latest) {
		return latest == null || date.isAfter(latest);
	}

}
