package net.yozmnews.digest.domain.prompt;

import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.user.SummaryLength;

/**
 * 프롬프트 구성의 상수 · 길이 기준 — 정본 — docs/specs/digest.md §5 · digest-R26
 * <p>
 * 길이 기준은 한 공급자 모델로 사용자 넷의 7일치 다이제스트를 만들어 재고 정했다. 기준은 출력 서술의 상한({@link DigestOutputSchema}) 안이다.
 */
public final class PromptRules {

	/** 다시 묻는 요청에 싣는 진단의 최대 코드 포인트 — 넘으면 앞부분만 */
	public static final int MAX_DIAGNOSIS = 200;

	/** 기사 줄 출처 이름 사이의 구분 */
	public static final String SOURCE_SEPARATOR = ", ";

	/** 기사 줄 발행 시각의 꼴 — 프로필 시간대의 날짜와 시각 */
	public static final String PUBLISHED_PATTERN = "yyyy-MM-dd HH:mm";

	private PromptRules() {
	}

	/** 주요 뉴스 요약({@code summary})의 길이 기준(코드 포인트) */
	public static Range summary(SummaryLength length) {
		return switch (length) {
			case SHORT -> new Range(100, 300);
			case NORMAL -> new Range(200, 500);
			case DETAILED -> new Range(400, 800);
		};
	}

	/** 개요({@code overview})의 길이 기준(코드 포인트) */
	public static Range overview(SummaryLength length) {
		return switch (length) {
			case SHORT -> new Range(250, 400);
			case NORMAL -> new Range(500, 800);
			case DETAILED -> new Range(800, 1_200);
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
