package net.yozmnews.evaluation.domain.result;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 평가 결과가 넘긴 체계와 맞는지 — docs/specs/evaluation.md §5.
 * <p>
 * 주제 중분류 · 지역 · 형식 선택지 · 중요도 수준의 키 집합이 체계와 정확히 같아야 하고, 원본 응답은 JSON 이고 어디에도 문자 U+0000 과 짝
 * 없는 서로게이트가 없어야 한다(PostgreSQL {@code jsonb} 가 둘 다 받지 않는다). 어긋남의 문구는 태그 종류 · 키만 담는다.
 */
public final class ResultCheck {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ResultCheck() {
	}

	/**
	 * @return 어긋남의 운영자용 문구 — 맞으면 빈 값
	 */
	public static Optional<String> mismatch(Evaluation evaluation, TagSetView tagSet) {
		Set<String> topics = tagSet.topics()
			.stream()
			.flatMap(category -> category.subtopics().stream())
			.map(AskedTag::key)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<String> regions = tagSet.regions().stream().map(AskedTag::key).collect(Collectors.toSet());
		Set<String> formats = tagSet.format().options().stream().map(FormatOption::key).collect(Collectors.toSet());
		Set<Integer> levels = tagSet.importance().levels().stream().map(ImportanceLevel::level).collect(Collectors.toSet());
		return keys("주제", topics, evaluation.topics())
			.or(() -> keys("지역", regions, evaluation.regions()))
			.or(() -> keys("형식", formats, evaluation.formats()))
			.or(() -> keys("중요도 수준", levels, evaluation.importance()))
			.or(() -> rawResponse(evaluation.rawResponse()));
	}

	private static <K> Optional<String> keys(String kind, Set<K> expected, Map<K, Double> actual) {
		if (expected.equals(actual.keySet())) {
			return Optional.empty();
		}
		Set<String> missing = new TreeSet<>();
		expected.stream().filter(key -> !actual.containsKey(key)).forEach(key -> missing.add(String.valueOf(key)));
		Set<String> unknown = new TreeSet<>();
		actual.keySet().stream().filter(key -> !expected.contains(key)).forEach(key -> unknown.add(String.valueOf(key)));
		return Optional.of(kind + " 키가 체계와 다르다 — 빠진 키 " + missing + ", 모르는 키 " + unknown);
	}

	private static Optional<String> rawResponse(String rawResponse) {
		JsonNode root;
		try {
			root = JSON.readTree(rawResponse);
		}
		catch (JacksonException ex) {
			return Optional.of("원본 응답이 JSON 이 아니다");
		}
		if (root == null || root.isMissingNode()) {
			return Optional.of("원본 응답이 JSON 이 아니다");
		}
		return unstorable(root);
	}

	/** jsonb 가 받지 않는 문자 — 문자열 값과 키를 모두 훑는다 */
	private static Optional<String> unstorable(JsonNode node) {
		if (node.isString()) {
			return unstorable(node.stringValue());
		}
		if (node.isObject()) {
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				Optional<String> found = unstorable(property.getKey()).or(() -> unstorable(property.getValue()));
				if (found.isPresent()) {
					return found;
				}
			}
		}
		if (node.isArray()) {
			for (JsonNode element : node.values()) {
				Optional<String> found = unstorable(element);
				if (found.isPresent()) {
					return found;
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<String> unstorable(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\u0000') {
				return Optional.of("원본 응답에 문자 U+0000 이 있다");
			}
			if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
				i++;
			}
			else if (Character.isSurrogate(c)) {
				return Optional.of("원본 응답에 짝 없는 서로게이트가 있다");
			}
		}
		return Optional.empty();
	}

}
