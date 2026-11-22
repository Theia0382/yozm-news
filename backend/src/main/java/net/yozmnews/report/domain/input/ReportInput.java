package net.yozmnews.report.domain.input;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.report.ReportSectionKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 생성의 입력 — 갈래 목록과 통계 스냅숏 · 정본 · docs/specs/report.md §5 · report-R3
 * <p>
 * 갈래는 재료가 있을 때만 든다 — 무엇이 재료인지는 재료를 만드는 작업이 정한다. 스냅숏의 꼴도 그 작업이 정하고, 여기서는 JSON 객체 하나인지만 본다.
 */
public record ReportInput(List<InputSection> sections, String statsSnapshot) {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.build();

	/**
	 * @throws IllegalArgumentException 갈래 종류가 겹치거나 스냅숏이 JSON 객체 하나가 아닐 때
	 */
	public ReportInput {
		sections = List.copyOf(sections);
		Objects.requireNonNull(statsSnapshot, "statsSnapshot");
		Set<ReportSectionKind> kinds = EnumSet.noneOf(ReportSectionKind.class);
		for (InputSection section : sections) {
			if (!kinds.add(section.kind())) {
				throw new IllegalArgumentException("갈래 종류가 겹친다: " + section.kind());
			}
		}
		if (!isObject(statsSnapshot)) {
			throw new IllegalArgumentException("스냅숏이 JSON 객체 하나가 아니다");
		}
	}

	private static boolean isObject(String text) {
		try {
			JsonNode node = JSON.readTree(text);
			return node != null && node.isObject();
		}
		catch (JacksonException ex) {
			return false;
		}
	}

}
