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

	private DigestScheduleRules() {
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
			if (inWindow && (latest == null || date.isAfter(latest))) {
				return Optional.of(date);
			}
		}
		return Optional.empty();
	}

}
