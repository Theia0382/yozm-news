package net.yozmnews.report.domain.output;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.platform.StructuredOutputSchema;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리포트 출력 서술 — 갈래 키를 입력 순서의 필수 칸으로, 갈래가 없으면 빈 객체, 키 이름.
 * <p>
 * 지키는 규칙: report-R5 · report-R6
 */
class ReportOutputSchemaTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void drawsTheInputKindsInInputOrderAsRequiredSectionFields() {
		ReportAliases aliases = ReportAliases.of(input(section(ReportSectionKind.COVERAGE, event(1)),
				section(ReportSectionKind.STORYLINES)));

		assertThat(JSON.readTree(StructuredOutputSchema.json(ReportOutputSchema.root(aliases))))
			.isEqualTo(JSON.readTree("""
					{
					  "$schema": "https://json-schema.org/draft/2020-12/schema",
					  "type": "object",
					  "properties": {
					    "overview": {"type": "string", "minLength": 1, "maxLength": 1500},
					    "sections": {
					      "type": "object",
					      "properties": {
					        "coverage": {"type": "object", "properties": {
					          "title": {"type": "string", "minLength": 1, "maxLength": 200},
					          "body": {"type": "string", "minLength": 1, "maxLength": 3000},
					          "refs": {"type": "array", "items": {"type": "string", "minLength": 1, "maxLength": 16}}},
					          "required": ["title", "body", "refs"], "additionalProperties": false},
					        "storylines": {"type": "object", "properties": {
					          "title": {"type": "string", "minLength": 1, "maxLength": 200},
					          "body": {"type": "string", "minLength": 1, "maxLength": 3000},
					          "refs": {"type": "array", "items": {"type": "string", "minLength": 1, "maxLength": 16}}},
					          "required": ["title", "body", "refs"], "additionalProperties": false}
					      },
					      "required": ["coverage", "storylines"],
					      "additionalProperties": false
					    }
					  },
					  "required": ["overview", "sections"],
					  "additionalProperties": false
					}"""));
		assertThat(JSON.readTree(StructuredOutputSchema.json(ReportOutputSchema.root(aliases)))
			.get("properties").get("sections").get("required").toString()).isEqualTo("[\"coverage\",\"storylines\"]");
	}

	@Test
	void drawsSectionsAsAnEmptyObjectWithoutKinds() {
		assertThat(JSON.readTree(StructuredOutputSchema.json(ReportOutputSchema.root(ReportAliases.of(input()))))
			.get("properties").get("sections")).isEqualTo(JSON.readTree("""
					{"type": "object", "properties": {}, "required": [], "additionalProperties": false}"""));
	}

	@Test
	void namesEveryKindAndRefusesTheOverview() {
		assertThat(ReportOutputSchema.key(ReportSectionKind.STORYLINES)).isEqualTo("storylines");
		assertThat(ReportOutputSchema.key(ReportSectionKind.COVERAGE)).isEqualTo("coverage");
		assertThat(ReportOutputSchema.key(ReportSectionKind.INTERESTS)).isEqualTo("interests");
		assertThat(ReportSectionKind.values()).containsExactly(ReportSectionKind.OVERVIEW, ReportSectionKind.STORYLINES,
				ReportSectionKind.COVERAGE, ReportSectionKind.INTERESTS);
		assertThatIllegalArgumentException().isThrownBy(() -> ReportOutputSchema.key(ReportSectionKind.OVERVIEW))
			.withMessage("개요는 갈래 칸이 아니다");
	}

}
