package net.yozmnews.source.domain.feed;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSS 의 RFC 822 날짜. 요일은 생략할 수 있고 이름만 확인한다(날짜와 대조하지 않는다). 연도는 4자리만 받는다 — 2자리 연도는 세기를
 * 정할 수 없어 해석 실패로 보낸다. 초와 시간대는 생략할 수 있고, 시간대가 없으면 주어진 시간대의 현지 시각으로 본다.
 */
final class Rfc822DateTime {

	private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
			"oct", "nov", "dec");

	/** RFC 822 가 정의한 이름 시간대 */
	private static final Map<String, ZoneOffset> NAMED_ZONES = Map.ofEntries(Map.entry("ut", ZoneOffset.UTC),
			Map.entry("utc", ZoneOffset.UTC), Map.entry("gmt", ZoneOffset.UTC), Map.entry("z", ZoneOffset.UTC),
			Map.entry("est", ZoneOffset.ofHours(-5)), Map.entry("edt", ZoneOffset.ofHours(-4)),
			Map.entry("cst", ZoneOffset.ofHours(-6)), Map.entry("cdt", ZoneOffset.ofHours(-5)),
			Map.entry("mst", ZoneOffset.ofHours(-7)), Map.entry("mdt", ZoneOffset.ofHours(-6)),
			Map.entry("pst", ZoneOffset.ofHours(-8)), Map.entry("pdt", ZoneOffset.ofHours(-7)));

	private static final Pattern FORMAT = Pattern.compile("(?:(?:mon|tue|wed|thu|fri|sat|sun)\\s*,\\s*)?"
			+ "(\\d{1,2})\\s+([a-z]{3})\\s+(\\d{4})\\s+(\\d{2}):(\\d{2})(?::(\\d{2}))?"
			+ "(?:\\s+(?:([+-])(\\d{2})(\\d{2})|(ut|utc|gmt|z|est|edt|cst|cdt|mst|mdt|pst|pdt)))?",
			Pattern.CASE_INSENSITIVE);

	private Rfc822DateTime() {
	}

	/**
	 * @param zone 시간대가 없는 값에 쓸 시간대
	 * @return 해석한 시각 — 형식이 아니거나 없는 날짜 · 시각이면 빈 값
	 */
	static Optional<Instant> parse(String text, ZoneId zone) {
		Matcher matcher = FORMAT.matcher(text.strip());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		int month = MONTHS.indexOf(matcher.group(2).toLowerCase(Locale.ROOT)) + 1;
		if (month == 0) {
			return Optional.empty();
		}
		try {
			LocalDateTime local = LocalDateTime.of(Integer.parseInt(matcher.group(3)), month,
					Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(4)),
					Integer.parseInt(matcher.group(5)), (matcher.group(6) != null) ? Integer.parseInt(matcher.group(6)) : 0);
			if (matcher.group(7) != null) {
				int minutes = Integer.parseInt(matcher.group(9));
				if (minutes > 59) {
					return Optional.empty();
				}
				int sign = matcher.group(7).equals("-") ? -1 : 1;
				ZoneOffset offset = ZoneOffset
					.ofTotalSeconds(sign * (Integer.parseInt(matcher.group(8)) * 3_600 + minutes * 60));
				return Optional.of(local.toInstant(offset));
			}
			if (matcher.group(10) != null) {
				return Optional.of(local.toInstant(NAMED_ZONES.get(matcher.group(10).toLowerCase(Locale.ROOT))));
			}
			return Optional.of(local.atZone(zone).toInstant());
		}
		catch (DateTimeException ex) {
			// 없는 날짜 · 시각, 범위 밖 오프셋
			return Optional.empty();
		}
	}

}
