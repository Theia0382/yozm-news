package net.yozmnews.report.domain.input;

import java.util.Objects;

import net.yozmnews.report.InterestShift;
import org.jspecify.annotations.Nullable;

/**
 * 주제 관심 줄 — 중분류 하나의 시작 · 끝 관심과 변화 · docs/specs/report.md §5 · report-R17
 *
 * @param key 중분류 전체 키(활성 버전)
 * @param start 기간 시작의 관심 x — 신호가 없으면 null
 * @param end 기간 끝의 관심 x — 신호가 없으면 null
 */
public record TopicInterestLine(String key, String name, @Nullable Double start, @Nullable Double end,
		InterestShift shift) {

	public TopicInterestLine {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(shift, "shift");
	}

}
