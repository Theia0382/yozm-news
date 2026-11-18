package net.yozmnews.digest.domain.prompt;

import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.user.SummaryLength;

/**
 * 프롬프트 구성의 상수 · 길이 기준 — 정본 — docs/specs/digest.md §5 · digest-R23
 * <p>
 * 길이 기준은 측정 전 목표다 — 실제 공급자로 만든 다이제스트의 출력 길이를 재서 고친다. 기준은 출력 서술의 상한({@link DigestOutputSchema}) 안이다.
 */
public final class PromptRules {

	/** 큰 스토리라인 묶음 별칭의 접두 — {@code S1}, {@code S2} … */
	public static final String STORYLINE_ALIAS_PREFIX = "S";

	/** 다시 묻는 요청에 싣는 진단의 최대 코드 포인트 — 넘으면 앞부분만 */
	public static final int MAX_DIAGNOSIS = 200;

	/** 후보 줄 출처 이름 사이의 구분 */
	public static final String SOURCE_SEPARATOR = ", ";

	/** 후보 줄 발행 시각의 꼴 — 프로필 시간대의 날짜와 시각 */
	public static final String PUBLISHED_PATTERN = "yyyy-MM-dd HH:mm";

	private PromptRules() {
	}

	/** 주요 뉴스 요약({@code summary})의 길이 기준(코드 포인트) */
	public static Range summary(SummaryLength length) {
		return switch (length) {
			case SHORT -> new Range(150, 300);
			case NORMAL -> new Range(300, 600);
			case DETAILED -> new Range(600, 1_200);
		};
	}

	/** 개요({@code overview})의 길이 기준(코드 포인트) */
	public static Range overview(SummaryLength length) {
		return switch (length) {
			case SHORT -> new Range(100, 250);
			case NORMAL -> new Range(200, 450);
			case DETAILED -> new Range(400, 800);
		};
	}

	/**
	 * 길이 범위 — 경계 포함
	 * @param min 하한
	 * @param max 상한
	 */
	public record Range(int min, int max) {

		/**
		 * @throws IllegalArgumentException 하한이 1 보다 작거나 상한보다 클 때
		 */
		public Range {
			if (min < 1 || min > max) {
				throw new IllegalArgumentException("길이 범위가 틀렸다: " + min + " ~ " + max);
			}
		}

	}

}
