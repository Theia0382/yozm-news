package net.yozmnews.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 출력 원문 읽기 — 정본 · docs/specs/platform.md §5 · platform-R31
 * <p>
 * 코드 펜스 한 겹을 벗기고 JSON 객체 하나로 읽은 뒤, 부르는 쪽의 서술을 따라 걸으며 필수 · 타입 · 상한 · 넘을 때의 처분을 가른다. 걸은 값은
 * {@link OutputValue} 로 모은다. 읽지 못하면 짧은 진단을 낸다 — 출력 원문을 싣지 않는다.
 */
public final class StructuredOutputReader {

	private static final String FENCE = "```";

	private static final String FENCE_LANGUAGE = "json";

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.build();

	private StructuredOutputReader() {
	}

	/**
	 * 출력 원문을 읽는다
	 * @param root 이번 요청의 출력 서술
	 */
	public static StructuredRead read(String text, OutputShape.Obj root) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(root, "root");
		JsonNode node;
		try {
			node = JSON.readTree(unfence(text.strip()));
		}
		catch (JacksonException ex) {
			return new StructuredRead.Unreadable("JSON 이 아니다");
		}
		if (node == null || node.isMissingNode()) {
			return new StructuredRead.Unreadable("JSON 이 아니다");
		}
		Walker walker = new Walker();
		return switch (walker.walk(node, root, "$")) {
			case Walked.Value value -> new StructuredRead.Read((OutputValue.Obj) Objects.requireNonNull(value.value(), "$"),
					walker.overLimit);
			case Walked.Drop drop -> new StructuredRead.Unreadable("$: 최상위를 버릴 수 없다");
			case Walked.Fail fail -> new StructuredRead.Unreadable(fail.reason());
		};
	}

	/** 앞뒤 공백을 지운 원문이 코드 펜스 한 겹으로 감싸여 있으면 벗긴다 */
	private static String unfence(String text) {
		if (!text.startsWith(FENCE) || !text.endsWith(FENCE) || text.length() < FENCE.length() * 2) {
			return text;
		}
		int lineEnd = text.indexOf('\n');
		if (lineEnd < 0) {
			return text;
		}
		String language = text.substring(FENCE.length(), lineEnd).strip();
		if (!language.isEmpty() && !language.equals(FENCE_LANGUAGE)) {
			return text;
		}
		int end = text.length() - FENCE.length();
		if (end < lineEnd + 1) {
			return text;
		}
		return text.substring(lineEnd + 1, end);
	}

	/** 한 마디를 걸은 결과 */
	private sealed interface Walked {

		/** 읽은 값 — {@code null} 은 없음 */
		record Value(@Nullable OutputValue value) implements Walked {
		}

		/** 이 값을 가진 버릴 수 있는 칸(키 꼴의 값 · 배열 원소)을 버린다 */
		record Drop() implements Walked {
		}

		/** 출력 전체를 읽지 못한다 */
		record Fail(String reason) implements Walked {
		}

	}

	/** 서술을 따라 걷는 한 번의 읽기 — 넘어서 버리거나 지운 수를 센다 */
	private static final class Walker {

		private int overLimit;

		Walked walk(JsonNode node, OutputShape shape, String path) {
			return switch (shape) {
				case OutputShape.Obj obj -> object(node, obj, path);
				case OutputShape.Arr arr -> array(node, arr, path);
				case OutputShape.Keyed keyed -> keyed(node, keyed, path);
				case OutputShape.Str str -> string(node, str, path);
			};
		}

		/**
		 * 속성을 모두 걷는다 — 어느 속성이든 읽지 못하면 객체를 버릴 속성이 있어도 읽지 못한 것이다. 버린 객체 안에서 센 수는 버린 칸
		 * 하나로 센다.
		 */
		private Walked object(JsonNode node, OutputShape.Obj shape, String path) {
			if (!node.isObject()) {
				return new Walked.Fail(path + ": 객체가 아니다");
			}
			int overLimitBefore = this.overLimit;
			boolean dropped = false;
			Map<String, OutputValue> values = new LinkedHashMap<>();
			for (OutputShape.Property property : shape.properties()) {
				String at = path + "." + property.name();
				JsonNode child = node.get(property.name());
				if (child == null || child.isMissingNode()) {
					if (property.required()) {
						return new Walked.Fail(at + ": 없다");
					}
					continue;
				}
				if (child.isNull()) {
					if (!property.nullable()) {
						return new Walked.Fail(at + ": null 이다");
					}
					continue;
				}
				Walked walked = walk(child, property.shape(), at);
				switch (walked) {
					case Walked.Value value -> {
						OutputValue read = value.value();
						if (read != null) {
							values.put(property.name(), read);
						}
					}
					case Walked.Drop drop -> dropped = true;
					case Walked.Fail fail -> {
						return fail;
					}
				}
			}
			if (dropped) {
				this.overLimit = overLimitBefore;
				return new Walked.Drop();
			}
			return new Walked.Value(new OutputValue.Obj(values));
		}

		/**
		 * 원소마다 걷는다 — 원소를 버리면 그 원소를 빼고 하나로 센다. {@code null} 원소 · 출력 전체를 읽지 못하게 하는 원소는 그대로 읽지 못한
		 * 것이다.
		 */
		private Walked array(JsonNode node, OutputShape.Arr shape, String path) {
			if (!node.isArray()) {
				return new Walked.Fail(path + ": 배열이 아니다");
			}
			List<OutputValue> values = new ArrayList<>();
			for (int index = 0; index < node.size(); index++) {
				String at = path + "[" + index + "]";
				JsonNode element = node.get(index);
				if (element.isNull()) {
					return new Walked.Fail(at + ": null 이다");
				}
				Walked walked = walk(element, shape.item(), at);
				switch (walked) {
					case Walked.Value value -> values.add(Objects.requireNonNull(value.value(), at));
					case Walked.Drop drop -> this.overLimit++;
					case Walked.Fail fail -> {
						return fail;
					}
				}
			}
			return new Walked.Value(new OutputValue.Arr(values));
		}

		/**
		 * 키마다 걷는다 — 없는 키 · {@code null} 인 키는 뺀다. 값을 버리면 그 키를 빼고 하나로 센다. 서술하지 않은 키는 무시한다. 값이 출력
		 * 전체를 읽지 못하게 하면 그대로 읽지 못한 것이다.
		 */
		private Walked keyed(JsonNode node, OutputShape.Keyed shape, String path) {
			if (!node.isObject()) {
				return new Walked.Fail(path + ": 객체가 아니다");
			}
			Map<String, OutputValue> values = new LinkedHashMap<>();
			for (String key : shape.keys()) {
				JsonNode child = node.get(key);
				if (child == null || child.isMissingNode() || child.isNull()) {
					continue;
				}
				Walked walked = walk(child, shape.value(), path + "." + key);
				switch (walked) {
					case Walked.Value value -> values.put(key, Objects.requireNonNull(value.value(), key));
					case Walked.Drop drop -> this.overLimit++;
					case Walked.Fail fail -> {
						return fail;
					}
				}
			}
			return new Walked.Value(new OutputValue.Obj(values));
		}

		private Walked string(JsonNode node, OutputShape.Str shape, String path) {
			if (!node.isString()) {
				return new Walked.Fail(path + ": 문자열이 아니다");
			}
			String value = node.stringValue().strip();
			if (value.isEmpty()) {
				return switch (shape.onBlank()) {
					case UNREADABLE -> new Walked.Fail(path + ": 비었다");
					case ABSENT -> new Walked.Value(null);
					case KEEP -> new Walked.Value(new OutputValue.Str(value));
				};
			}
			if (value.codePointCount(0, value.length()) <= shape.maxCodePoints()) {
				return new Walked.Value(new OutputValue.Str(value));
			}
			return switch (shape.onOverLimit()) {
				case UNREADABLE -> new Walked.Fail(path + ": " + shape.maxCodePoints() + " 코드 포인트를 넘는다");
				case DROP_ITEM -> new Walked.Drop();
				case DROP_VALUE -> {
					this.overLimit++;
					yield new Walked.Value(null);
				}
			};
		}

	}

}
