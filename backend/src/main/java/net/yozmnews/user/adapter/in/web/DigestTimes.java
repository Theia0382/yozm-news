package net.yozmnews.user.adapter.in.web;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 생성 시각의 화면 표기 {@code HH:mm} — 읽기 · 쓰기의 정본. docs/specs/user.md §5 · user-R50
 */
final class DigestTimes {

	private static final Pattern SHAPE = Pattern.compile("\\d{2}:\\d{2}");

	/** 엄격 해석 — {@code 24:00} 을 자정으로 받지 않는다 */
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm")
		.withResolverStyle(ResolverStyle.STRICT);

	private DigestTimes() {
	}

	/** {@code HH:mm} 을 읽는다 — 꼴이 틀리거나 없는 시각이면 빈 값 */
	static Optional<LocalTime> parse(String text) {
		if (!SHAPE.matcher(text).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(LocalTime.parse(text, FORMAT));
		}
		catch (DateTimeParseException ex) {
			return Optional.empty();
		}
	}

	static String format(LocalTime time) {
		return FORMAT.format(time);
	}

}
