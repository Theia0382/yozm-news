package net.yozmnews.llm.adapter.out.local;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 로컬 대체물의 생성 본문 — 출력 스키마를 따라 필수 속성만 채운 가장 작은 JSON · docs/specs/llm.md llm-R16.
 * <p>
 * 부르는 쪽의 읽기를 지나는 본문을 주는 것이 목적이다 — 내용의 뜻(다이제스트의 별칭 등)은 맞추지 않는다. 값 목록({@code enum})이 있으면 그
 * 첫 값, 문자열은 {@value #TEXT}(최대 길이가 더 짧으면 그만큼), 정수 · 수는 0, 불리언은 거짓, 배열은 최소 개수만큼, 객체는 필수 속성만,
 * 타입이 여럿이면 {@code null} 이 아닌 첫 타입이다.
 */
final class MinimalJson {

	/** 스키마가 없을 때의 본문이자 문자열 값 */
	static final String TEXT = "로컬 대체물이 만든 본문이다.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	private MinimalJson() {
	}

	static String of(@Nullable String schema) {
		if (schema == null) {
			return TEXT;
		}
		JsonNode root;
		try {
			root = JSON.readTree(schema);
		}
		catch (JacksonException ex) {
			return TEXT;
		}
		return JSON.writeValueAsString(value(root));
	}

	private static JsonNode value(JsonNode schema) {
		if (schema.get("enum") instanceof ArrayNode values && !values.isEmpty()) {
			return values.get(0);
		}
		return switch (type(schema)) {
			case "string" -> NODES.stringNode(text(schema));
			case "integer", "number" -> NODES.numberNode(0);
			case "boolean" -> NODES.booleanNode(false);
			case "array" -> array(schema);
			case "object" -> object(schema);
			default -> NODES.nullNode();
		};
	}

	/** 마디의 타입 — 여럿이면 {@code null} 이 아닌 첫 타입, 없으면 {@code properties} 가 있을 때 객체 */
	private static String type(JsonNode schema) {
		JsonNode type = schema.get("type");
		if (type != null && type.isString()) {
			return type.asString();
		}
		if (type instanceof ArrayNode types) {
			for (JsonNode each : types) {
				if (!"null".equals(each.asString())) {
					return each.asString();
				}
			}
			return "null";
		}
		return schema.has("properties") ? "object" : "null";
	}

	private static String text(JsonNode schema) {
		JsonNode max = schema.get("maxLength");
		if (max != null && max.canConvertToInt() && max.asInt() >= 0
				&& max.asInt() < TEXT.codePointCount(0, TEXT.length())) {
			return TEXT.substring(0, TEXT.offsetByCodePoints(0, max.asInt()));
		}
		return TEXT;
	}

	private static ArrayNode array(JsonNode schema) {
		ArrayNode array = NODES.arrayNode();
		JsonNode min = schema.get("minItems");
		int count = (min != null && min.canConvertToInt()) ? Math.max(0, min.asInt()) : 0;
		JsonNode items = schema.get("items");
		for (int i = 0; i < count; i++) {
			array.add((items != null) ? value(items) : NODES.nullNode());
		}
		return array;
	}

	private static ObjectNode object(JsonNode schema) {
		ObjectNode object = NODES.objectNode();
		Set<String> required = new HashSet<>();
		if (schema.get("required") instanceof ArrayNode names) {
			names.forEach(name -> required.add(name.asString()));
		}
		if (schema.get("properties") instanceof ObjectNode properties) {
			for (Map.Entry<String, JsonNode> property : properties.properties()) {
				if (required.contains(property.getKey())) {
					object.set(property.getKey(), value(property.getValue()));
				}
			}
		}
		return object;
	}

}
