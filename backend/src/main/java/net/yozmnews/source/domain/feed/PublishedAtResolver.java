package net.yozmnews.source.domain.feed;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import net.yozmnews.source.domain.profile.DatePattern;
import net.yozmnews.source.domain.profile.DateParsing;

/**
 * 발행 시각 원문 하나를 해석한다 — RFC 822 → ISO 8601 → 프로필의 날짜 형식 순으로 처음 성공한 값. 오프셋이 없는 값은 프로필 시간대의
 * 현지 시각이다. 받은 시각보다 {@link #FUTURE_TOLERANCE} 넘게 미래인 값은 시간대를 잘못 적은 것으로 보고 해석 실패로 돌린다.
 */
final class PublishedAtResolver {

	/** 받은 시각보다 이만큼까지 미래인 발행 시각은 받는다 */
	static final Duration FUTURE_TOLERANCE = Duration.ofHours(1);

	private PublishedAtResolver() {
	}

	/** @return 해석한 시각 — 해석하지 못했거나 너무 미래면 빈 값 */
	static Optional<Instant> resolve(String text, DateParsing dates, Instant fetchedAt) {
		String value = text.strip();
		if (value.isEmpty()) {
			return Optional.empty();
		}
		Instant latest = fetchedAt.plus(FUTURE_TOLERANCE);
		return parse(value, dates).filter(instant -> !instant.isAfter(latest));
	}

	private static Optional<Instant> parse(String value, DateParsing dates) {
		Optional<Instant> rfc822 = Rfc822DateTime.parse(value, dates.zone());
		if (rfc822.isPresent()) {
			return rfc822;
		}
		try {
			return Optional.of(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
		}
		catch (DateTimeException ex) {
			// 다음 형식
		}
		try {
			return Optional.of(LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(dates.zone()).toInstant());
		}
		catch (DateTimeException ex) {
			// 다음 형식
		}
		for (DatePattern pattern : dates.patterns()) {
			try {
				TemporalAccessor parsed = pattern.formatter()
					.parseBest(value, ZonedDateTime::from, OffsetDateTime::from, LocalDateTime::from);
				return Optional.of(switch (parsed) {
					case ZonedDateTime zoned -> zoned.toInstant();
					case OffsetDateTime offset -> offset.toInstant();
					case LocalDateTime local -> local.atZone(dates.zone()).toInstant();
					default -> throw new IllegalStateException("parseBest 가 요청하지 않은 형을 돌려줬다: " + parsed);
				});
			}
			catch (DateTimeException ex) {
				// 다음 형식
			}
		}
		return Optional.empty();
	}

}
