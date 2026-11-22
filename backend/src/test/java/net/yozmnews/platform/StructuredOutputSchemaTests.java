package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.platform.OutputShape.OnBlank;
import net.yozmnews.platform.OutputShape.OverLimit;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 출력 서술을 JSON Schema 문서로 그리기 · docs/specs/platform.md §5 · platform-R32
 */
class StructuredOutputSchemaTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void drawsEveryShapeWithClosedObjectsRequiredKeysAndStringBounds() {
		OutputShape.Obj root = new OutputShape.Obj(List.of(
				new OutputShape.Property("name", new OutputShape.Str(10, OnBlank.UNREADABLE, OverLimit.UNREADABLE), true,
						false),
				new OutputShape.Property("nick", new OutputShape.Str(5, OnBlank.ABSENT, OverLimit.DROP_VALUE), false, true),
				new OutputShape.Property("cells", new OutputShape.Keyed(List.of("a", "b"),
						new OutputShape.Obj(List.of(new OutputShape.Property("title",
								new OutputShape.Str(4, OnBlank.UNREADABLE, OverLimit.DROP_ITEM), true, false)))),
						true, false),
				new OutputShape.Property("lines", new OutputShape.Arr(new OutputShape.Str(3, OnBlank.KEEP, OverLimit.DROP_ITEM)),
						true, false)));

		assertThat(JSON.readTree(StructuredOutputSchema.json(root))).isEqualTo(JSON.readTree("""
				{
				  "$schema": "https://json-schema.org/draft/2020-12/schema",
				  "type": "object",
				  "properties": {
				    "name": {"type": "string", "minLength": 1, "maxLength": 10},
				    "nick": {"type": ["string", "null"], "maxLength": 5},
				    "cells": {
				      "type": "object",
				      "properties": {
				        "a": {"type": "object", "properties": {"title": {"type": "string", "minLength": 1, "maxLength": 4}},
				              "required": ["title"], "additionalProperties": false},
				        "b": {"type": "object", "properties": {"title": {"type": "string", "minLength": 1, "maxLength": 4}},
				              "required": ["title"], "additionalProperties": false}
				      },
				      "required": ["a", "b"],
				      "additionalProperties": false
				    },
				    "lines": {"type": "array", "items": {"type": "string", "minLength": 1, "maxLength": 3}}
				  },
				  "required": ["name", "cells", "lines"],
				  "additionalProperties": false
				}"""));
	}

	@Test
	void drawsAnObjectWithoutPropertiesAsAClosedEmptyObject() {
		OutputShape.Obj root = new OutputShape.Obj(
				List.of(new OutputShape.Property("cells", new OutputShape.Obj(List.of()), true, false)));

		assertThat(JSON.readTree(StructuredOutputSchema.json(root)).get("properties").get("cells")).isEqualTo(JSON.readTree("""
				{"type": "object", "properties": {}, "required": [], "additionalProperties": false}"""));
	}

}
