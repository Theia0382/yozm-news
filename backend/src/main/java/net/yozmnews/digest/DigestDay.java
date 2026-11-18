package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 날짜 하나의 다이제스트 — 보이는 다이제스트와 가장 최근 시도, 그 날짜의 사용량. {@link DigestDirectory} 가 낸다. docs/specs/digest.md §3 ·
 * digest-R38
 *
 * @param date 다이제스트 날짜
 * @param previousDate 그 사용자의 다이제스트 행이 있는 날짜 가운데 바로 앞 — 없으면 null
 * @param nextDate 그 사용자의 다이제스트 행이 있는 날짜 가운데 바로 뒤 — 없으면 null
 * @param shown 그 날짜의 보이는 다이제스트(digest-R13) — 없으면 null
 * @param latest 그 날짜의 가장 최근(ID 가 가장 큰) 시도 — {@code shown} 과 같은 행일 수 있다
 * @param usage 그 날짜 모든 시도의 수와 토큰 합
 */
public record DigestDay(LocalDate date, @Nullable LocalDate previousDate, @Nullable LocalDate nextDate,
		@Nullable ShownDigest shown, DigestAttempt latest, DigestDayUsage usage) {

	public DigestDay {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(latest, "latest");
		Objects.requireNonNull(usage, "usage");
	}

}
