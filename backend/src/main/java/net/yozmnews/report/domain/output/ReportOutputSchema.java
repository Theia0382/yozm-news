package net.yozmnews.report.domain.output;

import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.OutputShape.OnBlank;
import net.yozmnews.platform.OutputShape.OverLimit;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.AliasedSection;
import net.yozmnews.report.domain.alias.ReportAliases;

/**
 * 리포트 출력 구조 · 상한 · 키 이름 — 정본 · docs/specs/report.md §5 · report-R5 · report-R6
 * <p>
 * 출력 서술은 요청마다 그 갈래 집합에서 만든다({@link #root}) — 개요와, 입력 갈래의 키를 입력 순서로 적은 키 꼴. 같은 갈래 집합이면 같은 서술이다.
 * 생성에 넘기는 JSON Schema 문서는 서술을 그린 것({@code StructuredOutputSchema.json})이다. 문자열 상한은 측정 전 목표다.
 */
public final class ReportOutputSchema {

	/** {@code overview} 의 최대 코드 포인트 */
	public static final int MAX_OVERVIEW = 1_500;

	/** 갈래 칸 {@code title} 의 최대 코드 포인트 */
	public static final int MAX_TITLE = 200;

	/** 갈래 칸 {@code body} 의 최대 코드 포인트 */
	public static final int MAX_BODY = 3_000;

	/** {@code refs} 원소(별칭)의 최대 코드 포인트 — 넘으면 그 원소를 버린다 */
	public static final int MAX_ALIAS = 16;

	/** 기간의 개요 */
	public static final String OVERVIEW = "overview";

	/** 갈래 칸 — 갈래 키를 키로 한 객체 */
	public static final String SECTIONS = "sections";

	/** 갈래 칸의 제목 */
	public static final String TITLE = "title";

	/** 갈래 칸의 본문 */
	public static final String BODY = "body";

	/** 갈래 칸이 인용한 별칭 */
	public static final String REFS = "refs";

	private static final OutputShape.Obj SECTION = new OutputShape.Obj(List.of(
			new OutputShape.Property(TITLE, new OutputShape.Str(MAX_TITLE, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true,
					false),
			new OutputShape.Property(BODY, new OutputShape.Str(MAX_BODY, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true,
					false),
			new OutputShape.Property(REFS,
					new OutputShape.Arr(new OutputShape.Str(MAX_ALIAS, OnBlank.KEEP, OverLimit.DROP_ITEM)), true, false)));

	/** 갈래가 없는 칸의 서술 — 키 없는 빈 객체 */
	private static final OutputShape.Obj EMPTY = new OutputShape.Obj(List.of());

	private ReportOutputSchema() {
	}

	/**
	 * 이번 요청의 출력 서술 — 개요와, 입력 갈래의 키(입력 순서)를 키로 한 갈래 칸(갈래가 없으면 빈 객체)
	 */
	public static OutputShape.Obj root(ReportAliases aliases) {
		Objects.requireNonNull(aliases, "aliases");
		List<String> keys = aliases.sections().stream().map(AliasedSection::kind).map(ReportOutputSchema::key).toList();
		OutputShape sections = keys.isEmpty() ? EMPTY : new OutputShape.Keyed(keys, SECTION);
		return new OutputShape.Obj(List.of(
				new OutputShape.Property(OVERVIEW, new OutputShape.Str(MAX_OVERVIEW, OnBlank.UNREADABLE, OverLimit.UNREADABLE),
						true, false),
				new OutputShape.Property(SECTIONS, sections, true, false)));
	}

	/**
	 * 갈래 칸의 키 이름 — 정본
	 * @throws IllegalArgumentException 개요일 때 — 개요는 갈래가 아니다
	 */
	public static String key(ReportSectionKind kind) {
		Objects.requireNonNull(kind, "kind");
		return switch (kind) {
			case STORYLINES -> "storylines";
			case COVERAGE -> "coverage";
			case INTERESTS -> "interests";
			case OVERVIEW -> throw new IllegalArgumentException("개요는 갈래 칸이 아니다");
		};
	}

}
