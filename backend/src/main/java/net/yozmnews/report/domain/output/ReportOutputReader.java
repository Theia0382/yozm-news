package net.yozmnews.report.domain.output;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.OutputValue;
import net.yozmnews.platform.StructuredOutputReader;
import net.yozmnews.platform.StructuredRead;
import net.yozmnews.report.ReportSectionKind;

/**
 * 리포트 출력 원문 읽기 · docs/specs/report.md §5 · report-R5
 * <p>
 * 서술({@link ReportOutputSchema#root})을 따라 읽는 것은 {@code platform} 의 {@link StructuredOutputReader} 이고, 이 클래스는 걸은 값을
 * {@link ReportDraft} 로 옮긴다. 별칭을 입력과 견주지 않는다 — 대조는 {@link ReportCitationCheck} 가 한다.
 */
public final class ReportOutputReader {

	private ReportOutputReader() {
	}

	/**
	 * 출력 원문을 읽는다
	 * @param root 이번 요청의 출력 서술({@link ReportOutputSchema#root})
	 */
	public static ReportOutputRead read(String text, OutputShape.Obj root) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(root, "root");
		return switch (StructuredOutputReader.read(text, root)) {
			case StructuredRead.Read read -> new ReportOutputRead.Read(draft(read.root()), read.overLimit());
			case StructuredRead.Unreadable unreadable -> new ReportOutputRead.Unreadable(unreadable.reason());
		};
	}

	private static ReportDraft draft(OutputValue.Obj root) {
		OutputValue.Obj written = root.obj(ReportOutputSchema.SECTIONS);
		Map<ReportSectionKind, SectionDraft> sections = new EnumMap<>(ReportSectionKind.class);
		for (ReportSectionKind kind : ReportSectionKind.values()) {
			String key = ReportOutputSchema.key(kind);
			if (!written.values().containsKey(key)) {
				continue;
			}
			OutputValue.Obj section = written.obj(key);
			List<String> refs = section.arr(ReportOutputSchema.REFS)
				.items()
				.stream()
				.map(ref -> ((OutputValue.Str) ref).value())
				.toList();
			sections.put(kind, new SectionDraft(section.string(ReportOutputSchema.TITLE),
					section.string(ReportOutputSchema.BODY), refs));
		}
		return new ReportDraft(sections);
	}

}
