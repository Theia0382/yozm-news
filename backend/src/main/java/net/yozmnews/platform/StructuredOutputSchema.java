package net.yozmnews.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 출력 서술을 JSON Schema 문서로 그리기 — 정본 · docs/specs/platform.md §5 · platform-R32
 * <p>
 * 생성에 넘기는 문서라 읽기({@link StructuredOutputReader})보다 엄격하다 — 모르는 속성 · 빠진 키 꼴의 키를 받지 않는다.
 */
public final class StructuredOutputSchema {

	private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private StructuredOutputSchema() {
	}

	/**
	 * 서술을 JSON Schema(draft 2020-12) 문서로 그린다 — 객체는 모르는 속성을 허용하지 않고, 비었을 때 없음으로 받지 않는
	 * 문자열은 {@code minLength} 1, null 을 받는 속성은 타입에 {@code "null"} 을 더한다. 생성에 넘기는 문서라 읽기보다 엄격하다.
	 */
	public static String json(OutputShape.Obj root) {
		Objects.requireNonNull(root, "root");
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("$schema", DIALECT);
		document.putAll(draw(root, false));
		return JSON.writeValueAsString(document);
	}

	private static Map<String, Object> draw(OutputShape shape, boolean nullable) {
		Map<String, Object> node = new LinkedHashMap<>();
		switch (shape) {
			case OutputShape.Obj obj -> {
				node.put("type", type("object", nullable));
				Map<String, Object> properties = new LinkedHashMap<>();
				List<String> required = new ArrayList<>();
				for (OutputShape.Property property : obj.properties()) {
					properties.put(property.name(), draw(property.shape(), property.nullable()));
					if (property.required()) {
						required.add(property.name());
					}
				}
				node.put("properties", properties);
				node.put("required", required);
				node.put("additionalProperties", false);
			}
			case OutputShape.Arr arr -> {
				node.put("type", type("array", nullable));
				node.put("items", draw(arr.item(), false));
			}
			case OutputShape.Keyed keyed -> {
				node.put("type", type("object", nullable));
				Map<String, Object> properties = new LinkedHashMap<>();
				for (String key : keyed.keys()) {
					properties.put(key, draw(keyed.value(), false));
				}
				node.put("properties", properties);
				node.put("required", keyed.keys());
				node.put("additionalProperties", false);
			}
			case OutputShape.Str str -> {
				node.put("type", type("string", nullable));
				boolean rejectsBlank = switch (str.onBlank()) {
					case UNREADABLE, KEEP -> true;
					case ABSENT -> false;
				};
				if (rejectsBlank) {
					node.put("minLength", 1);
				}
				node.put("maxLength", str.maxCodePoints());
			}
		}
		return node;
	}

	private static Object type(String type, boolean nullable) {
		return nullable ? List.of(type, "null") : type;
	}

}
