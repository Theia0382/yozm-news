package net.yozmnews.source.domain.profile;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 발행 시각 해석 설정 — source-R40.
 *
 * @param patterns 추가로 시도할 날짜 형식
 * @param zone 오프셋 없는 값의 시간대
 */
public record DateParsing(List<DatePattern> patterns, ZoneId zone) {

	/** 프로필이 시간대를 적지 않았을 때의 시간대 */
	public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	public DateParsing {
		patterns = List.copyOf(patterns);
		Objects.requireNonNull(zone, "zone");
	}

}
