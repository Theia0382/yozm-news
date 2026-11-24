package net.yozmnews.report.domain.prompt;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.TokenEstimate;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.output.ReportOutputSchema;

/**
 * 프롬프트 구성의 상수 · 길이 기준 · 출력 본문 토큰 — 정본 · docs/specs/report.md §5 · report-R23 · report-R24
 * <p>
 * 길이 기준과 몫은 측정 전 목표다 — 길이 기준의 위쪽은 출력 서술의 상한({@link ReportOutputSchema}) 안이다. 출력 본문 토큰은 서술의 상한에서
 * 낸다 — 서술의 상한을 넘는 출력은 읽지 못하거나 버려지므로 그보다 크게 두지 않는다.
 */
public final class ReportPromptRules {

	/** 갈래 칸 제목의 길이 기준(코드 포인트 안팎) */
	public static final int TITLE_LENGTH = 40;

	/** 다시 묻는 프롬프트에 싣는 진단의 최대 코드 포인트 — 넘으면 앞부분만 */
	public static final int MAX_DIAGNOSIS = 200;

	/** 사용자 구획에 싣는 관심 값의 소수 자리 */
	public static final int INTEREST_SCALE = 2;

	/** 출력 본문 토큰에서 갈래 칸마다 별칭 배열의 몫(코드 포인트) — {@code refs} 는 원소 수의 상한이 없다 */
	public static final int REFS_ALLOWANCE = 1_000;

	/** 출력 본문 토큰에서 키 · 괄호의 몫(코드 포인트) */
	public static final int JSON_ALLOWANCE = 500;

	/** 출력에 쓸 수 있는 갈래 수의 최댓값 — 개요가 아닌 섹션 종류의 수 */
	private static final int MAX_SECTIONS = ReportSectionKind.values().length - 1;

	private ReportPromptRules() {
	}

	/** 개요({@code overview})의 길이 기준(코드 포인트) */
	public static Range overview(ReportPeriodKind kind) {
		return switch (kind) {
			case WEEK -> new Range(300, 600);
			case MONTH -> new Range(600, 1_200);
		};
	}

	/** 갈래 칸 본문({@code body})의 길이 기준(코드 포인트) */
	public static Range body(ReportPeriodKind kind) {
		return switch (kind) {
			case WEEK -> new Range(300, 900);
			case MONTH -> new Range(600, 1_800);
		};
	}

	/**
	 * 한 호출의 출력 본문 토큰 상한 — 개요 · 갈래마다 제목과 본문의 서술 상한에 갈래마다 {@link #REFS_ALLOWANCE} 와 {@link #JSON_ALLOWANCE}
	 * 를 더한 코드 포인트의 어림. 관문의 상한({@link GenerationRequest#MAX_BODY_TOKENS})을 넘지 않는다
	 * @param sections 이번 요청의 갈래 수
	 * @throws IllegalArgumentException 갈래 수가 1 보다 작거나 갈래 종류의 수보다 클 때
	 */
	public static int bodyTokens(int sections) {
		if (sections < 1 || sections > MAX_SECTIONS) {
			throw new IllegalArgumentException("갈래 수는 1 ~ " + MAX_SECTIONS + " 이다: " + sections);
		}
		int codePoints = ReportOutputSchema.MAX_OVERVIEW
				+ sections * (ReportOutputSchema.MAX_TITLE + ReportOutputSchema.MAX_BODY + REFS_ALLOWANCE) + JSON_ALLOWANCE;
		return Math.min(TokenEstimate.tokens(codePoints), GenerationRequest.MAX_BODY_TOKENS);
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
