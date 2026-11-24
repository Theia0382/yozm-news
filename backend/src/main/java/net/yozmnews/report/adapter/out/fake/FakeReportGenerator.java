package net.yozmnews.report.adapter.out.fake;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.report.domain.alias.AliasedSection;
import net.yozmnews.report.domain.generator.ReportGenerator;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.output.ReportOutputSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리포트 생성기의 로컬 대체물 — 외부 호출 없이 고정 규칙으로 출력 원문을 만든다 · docs/specs/report.md §4 · report-R10
 * <p>
 * 요청의 별칭 갈래만 쓴다 — 모델 · 추론 강도 · 프롬프트는 보지 않는다. 갈래마다 그 갈래 대상의 별칭으로만 인용한다. 결과는 언제나 끝까지 쓴 응답이다.
 */
public final class FakeReportGenerator implements ReportGenerator {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public ReportOutcome generate(ReportRequest request) {
		Objects.requireNonNull(request, "request");
		List<AliasedSection> sections = request.aliases().sections();
		Map<String, Object> written = new LinkedHashMap<>();
		for (AliasedSection section : sections) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put(ReportOutputSchema.TITLE, "로컬 섹션 " + (written.size() + 1));
			entry.put(ReportOutputSchema.BODY, "로컬 대체물이 만든 본문이다.");
			entry.put(ReportOutputSchema.REFS, section.aliases());
			written.put(ReportOutputSchema.key(section.kind()), entry);
		}
		Map<String, Object> output = new LinkedHashMap<>();
		output.put(ReportOutputSchema.OVERVIEW, "로컬 대체물이 만든 리포트다. 섹션 " + sections.size() + "개.");
		output.put(ReportOutputSchema.SECTIONS, written);
		return new ReportOutcome.Generated(JSON.writeValueAsString(output), new TokenUsage(0, 0), GenerationStop.COMPLETE);
	}

}
