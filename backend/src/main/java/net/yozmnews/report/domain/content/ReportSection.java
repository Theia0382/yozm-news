package net.yozmnews.report.domain.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.output.CitedReport;
import net.yozmnews.report.domain.output.CitedSection;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 리포트 내용 aggregate — 섹션 하나와 그 인용 · docs/specs/report.md §4 · report-R9
 * <p>
 * 끝날 때 한 번 만들어 쓰고 바꾸지 않는다.
 */
@Table("report_sections")
public final class ReportSection {

	@Id
	private @Nullable Long id;

	private final long reportId;

	private final ReportSectionKind kind;

	private final int position;

	private final @Nullable String title;

	private final String body;

	@MappedCollection(idColumn = "report_section_id", keyColumn = "position")
	private final List<ReportItem> items;

	@PersistenceCreator
	ReportSection(@Nullable Long id, long reportId, ReportSectionKind kind, int position, @Nullable String title,
			String body, List<ReportItem> items) {
		this.id = id;
		this.reportId = reportId;
		this.kind = Objects.requireNonNull(kind, "kind");
		this.position = position;
		this.title = title;
		this.body = Objects.requireNonNull(body, "body");
		this.items = List.copyOf(items);
	}

	/**
	 * 대조한 출력을 섹션으로 나눈다 — 개요(0, 제목 · 인용 없음), 대조를 지난 갈래 섹션마다(1 …, 입력 순서 — 제목 · 본문 · 인용)
	 */
	public static List<ReportSection> of(ReportId report, CitedReport cited) {
		Objects.requireNonNull(report, "report");
		Objects.requireNonNull(cited, "cited");
		List<ReportSection> sections = new ArrayList<>();
		sections.add(new ReportSection(null, report.value(), ReportSectionKind.OVERVIEW, 0, null, cited.overview(),
				List.of()));
		for (CitedSection section : cited.sections()) {
			sections.add(new ReportSection(null, report.value(), section.kind(), sections.size(), section.title(),
					section.body(), section.citations().stream().map(ReportItem::of).toList()));
		}
		return List.copyOf(sections);
	}

	/**
	 * @return 저장소가 매긴 식별자 — 저장 전이면 빈 값
	 */
	public Optional<Long> id() {
		return Optional.ofNullable(this.id);
	}

	public ReportId reportId() {
		return new ReportId(this.reportId);
	}

	public ReportSectionKind kind() {
		return this.kind;
	}

	/** 리포트 안의 순서 */
	public int position() {
		return this.position;
	}

	/**
	 * @return 갈래 섹션의 제목 — 개요면 빈 값
	 */
	public Optional<String> title() {
		return Optional.ofNullable(this.title);
	}

	public String body() {
		return this.body;
	}

	/** 인용 — 순서대로. 개요는 비었다 */
	public List<ReportItem> items() {
		return this.items;
	}

}
