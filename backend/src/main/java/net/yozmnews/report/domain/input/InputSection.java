package net.yozmnews.report.domain.input;

import java.util.List;

import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportSectionKind;

/**
 * 생성의 입력의 갈래 하나 — 종류마다 재료의 꼴이 정해진 봉인 타입 · docs/specs/report.md §5 · report-R3 · report-R14
 * <p>
 * 갈래의 종류와 인용 대상은 재료에서 나온다 — 따로 들지 않아 재료와 어긋날 수 없다.
 */
public sealed interface InputSection permits StorylinesSection, InterestsSection {

	/** 갈래 종류 */
	ReportSectionKind kind();

	/** 인용 대상 — 재료의 순서, 같은 대상은 처음 것만(report-R14) */
	List<ReportCitation> targets();

}
