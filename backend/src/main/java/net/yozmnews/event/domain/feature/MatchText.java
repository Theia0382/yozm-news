package net.yozmnews.event.domain.feature;

import java.util.Objects;

import net.yozmnews.platform.CodePoints;
import org.jspecify.annotations.Nullable;

/**
 * 표현 텍스트 구성의 정본 — 제목 + 요약문 앞부분 · docs/specs/event.md §4 · event-R1.
 * <p>
 * 임베딩과 핵심 말이 같은 텍스트를 쓴다.
 */
public final class MatchText {

	/** 요약문 앞부분의 길이(코드 포인트) — 측정 전 목표 */
	public static final int SUMMARY_PREFIX_CODE_POINTS = 300;

	private MatchText() {
	}

	/**
	 * 제목에, 요약문이 있으면 줄바꿈 하나와 요약문 앞 {@value #SUMMARY_PREFIX_CODE_POINTS} 코드 포인트를 잇는다. 요약문은 앞뒤 공백을
	 * ({@link String#strip()}) 걷은 뒤 자르고, 코드 포인트 단위라 서로게이트 쌍을 가르지 않는다. 요약문이 null 이거나 공백뿐이면 제목만이다.
	 * 제목은 고치지 않는다.
	 * @throws IllegalArgumentException 제목이 공백뿐일 때
	 */
	public static String compose(String title, @Nullable String summary) {
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) {
			throw new IllegalArgumentException("제목이 비었다");
		}
		if (summary == null || summary.isBlank()) {
			return title;
		}
		return title + "\n" + CodePoints.prefix(summary.strip(), SUMMARY_PREFIX_CODE_POINTS);
	}

}
