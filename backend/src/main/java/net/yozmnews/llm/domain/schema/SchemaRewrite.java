package net.yozmnews.llm.domain.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 출력 JSON Schema 를 공급자가 받는 부분집합으로 다시 쓰는 걸음 — 두 공급자의 맞춤이 함께 쓴다 · docs/specs/llm.md llm-R21.
 * <p>
 * 스키마 마디마다 허용 키워드 밖을 지우고, 객체 마디에는 {@code additionalProperties: false} 를 둔다. {@code properties} 아래의 키는
 * 속성 이름이라 거르지 않고 값만 다시 쓴다. 참조({@code $ref} · {@code $defs})는 허용 키워드가 아니라 지워진다 — 참조를 쓰는 스키마는
 * 맞춤 뒤 뜻이 바뀐다.
 */
final class SchemaRewrite {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Set<String> allowed;

	private final boolean requireAll;

	/**
	 * @param allowed 남길 키워드
	 * @param requireAll 참이면 객체의 모든 속성을 {@code required} 에 넣고, 원래 필수가 아니던 속성이 null 을 받게 한다(타입의
	 * {@code "null"} · 값 목록의 null)
	 */
	SchemaRewrite(Set<String> allowed, boolean requireAll) {
		this.allowed = Set.copyOf(allowed);
		this.requireAll = requireAll;
	}

	/**
	 * @throws IllegalArgumentException JSON 객체 문서가 아닐 때
	 */
	String apply(String schema) {
		Objects.requireNonNull(schema, "schema");
		ObjectNode root = SchemaDocuments.read(schema)
			.orElseThrow(() -> new IllegalArgumentException("출력 스키마는 JSON 객체 문서다"));
		return JSON.writeValueAsString(rewrite(root));
	}

	private ObjectNode rewrite(ObjectNode node) {
		ObjectNode out = JSON.createObjectNode();
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			if (this.allowed.contains(entry.getKey())) {
				out.set(entry.getKey(), entry.getValue().deepCopy());
			}
		}
		if (out.get("properties") instanceof ObjectNode properties) {
			ObjectNode rewritten = JSON.createObjectNode();
			for (Map.Entry<String, JsonNode> property : properties.properties()) {
				rewritten.set(property.getKey(), (property.getValue() instanceof ObjectNode child) ? rewrite(child)
						: property.getValue());
			}
			out.set("properties", rewritten);
			if (this.requireAll) {
				requireAll(out, rewritten);
			}
		}
		if (out.get("items") instanceof ObjectNode items) {
			out.set("items", rewrite(items));
		}
		if (isObject(out)) {
			out.put("additionalProperties", false);
		}
		return out;
	}

	private static void requireAll(ObjectNode node, ObjectNode properties) {
		List<String> before = new ArrayList<>();
		if (node.get("required") instanceof ArrayNode required) {
			required.forEach(name -> before.add(name.asString()));
		}
		ArrayNode all = JSON.createArrayNode();
		for (String name : properties.propertyNames()) {
			all.add(name);
			if (!before.contains(name) && properties.get(name) instanceof ObjectNode property) {
				allowNull(property);
			}
		}
		node.set("required", all);
	}

	/**
	 * 마디가 null 을 받게 한다 — 타입에 {@code "null"} 을, 값 목록({@code enum})에 null 을 더한다. 둘 다 없는 마디는 이미 null 을
	 * 받으므로 그대로 둔다.
	 */
	private static void allowNull(ObjectNode property) {
		if (property.get("enum") instanceof ArrayNode values && !containsNull(values)) {
			values.addNull();
		}
		JsonNode type = property.get("type");
		if (type == null) {
			return;
		}
		if (type.isString()) {
			if (!"null".equals(type.asString())) {
				ArrayNode types = JSON.createArrayNode();
				types.add(type.asString());
				types.add("null");
				property.set("type", types);
			}
			return;
		}
		if (type instanceof ArrayNode types) {
			for (JsonNode each : types) {
				if ("null".equals(each.asString())) {
					return;
				}
			}
			types.add("null");
		}
	}

	private static boolean containsNull(ArrayNode values) {
		for (JsonNode each : values) {
			if (each.isNull()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isObject(ObjectNode node) {
		if (node.has("properties")) {
			return true;
		}
		JsonNode type = node.get("type");
		if (type == null) {
			return false;
		}
		if (type.isString()) {
			return "object".equals(type.asString());
		}
		for (JsonNode each : type) {
			if ("object".equals(each.asString())) {
				return true;
			}
		}
		return false;
	}

}
