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
 * 출력 JSON Schema 를 공급자가 받는 부분집합으로 다시 쓰는 걸음 — 공급자마다의 맞춤이 함께 쓴다 · docs/specs/llm.md llm-R21.
 * <p>
 * 스키마 마디마다 허용 키워드 밖을 지우고, 객체 마디에는 {@code additionalProperties: false} 를 둔다. {@code properties} 아래의 키는
 * 속성 이름이라 거르지 않고 값만 다시 쓴다. 참조({@code $ref} · {@code $defs})는 허용 키워드가 아니라 지워진다 — 참조를 쓰는 스키마는
 * 맞춤 뒤 뜻이 바뀐다.
 */
final class SchemaRewrite {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final Set<String> allowed;

	private final Required required;

	/**
	 * @param allowed 남길 키워드
	 * @param required 객체의 {@code required} 를 다루는 꼴
	 */
	SchemaRewrite(Set<String> allowed, Required required) {
		this.allowed = Set.copyOf(allowed);
		this.required = Objects.requireNonNull(required, "required");
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
			Runnable require = switch (this.required) {
				case AS_GIVEN -> () -> {
				};
				case ALL_NULL_IN_TYPE_AND_VALUES -> () -> requireAll(out, rewritten, false);
				case ALL_NULL_IN_VALUES_WITHOUT_TYPE -> () -> requireAll(out, rewritten, true);
			};
			require.run();
		}
		if (out.get("items") instanceof ObjectNode items) {
			out.set("items", rewrite(items));
		}
		if (isObject(out)) {
			out.put("additionalProperties", false);
		}
		return out;
	}

	/**
	 * @param valuesWithoutType 참이면 값 목록이 있는 마디는 타입을 지우고 값 목록으로만 null 을 받게 한다
	 */
	private static void requireAll(ObjectNode node, ObjectNode properties, boolean valuesWithoutType) {
		List<String> before = new ArrayList<>();
		if (node.get("required") instanceof ArrayNode required) {
			required.forEach(name -> before.add(name.asString()));
		}
		ArrayNode all = JSON.createArrayNode();
		for (String name : properties.propertyNames()) {
			all.add(name);
			if (!before.contains(name) && properties.get(name) instanceof ObjectNode property) {
				allowNull(property, valuesWithoutType);
			}
		}
		node.set("required", all);
	}

	/**
	 * 마디가 null 을 받게 한다 — 타입에 {@code "null"} 을, 값 목록({@code enum})에 null 을 더한다. 둘 다 없는 마디는 이미 null 을
	 * 받으므로 그대로 둔다.
	 *
	 * @param valuesWithoutType 참이면 값 목록이 있는 마디는 타입을 지우고 값 목록에만 null 을 더한다 — 값 목록과 null 을 더한 타입 배열을
	 * 함께 두면 Anthropic 구조화 출력이 값과 타입이 맞지 않는다며 거절한다
	 */
	private static void allowNull(ObjectNode property, boolean valuesWithoutType) {
		if (property.get("enum") instanceof ArrayNode values) {
			if (!containsNull(values)) {
				values.addNull();
			}
			if (valuesWithoutType) {
				property.remove("type");
				return;
			}
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

	/** 객체의 {@code required} 를 다루는 꼴 */
	enum Required {

		/** 그대로 둔다 */
		AS_GIVEN,

		/** 모든 속성을 넣고, 원래 선택이던 속성은 타입에 {@code "null"} · 값 목록에 null 을 더해 null 을 받게 한다 */
		ALL_NULL_IN_TYPE_AND_VALUES,

		/**
		 * 모든 속성을 넣고, 원래 선택이던 속성은 null 을 받게 한다 — 값 목록이 있으면 타입을 지우고 값 목록에 null 을, 없으면 타입에
		 * {@code "null"} 을 더한다
		 */
		ALL_NULL_IN_VALUES_WITHOUT_TYPE

	}

}
