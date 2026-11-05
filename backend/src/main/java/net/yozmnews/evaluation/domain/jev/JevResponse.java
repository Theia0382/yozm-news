package net.yozmnews.evaluation.domain.jev;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jev 의 200 응답을 포트 결과로 옮긴다 — docs/specs/evaluation.md §4.
 * <p>
 * 답은 {@link JevRequest} 의 질문 이름으로 읽는다. 중요도는 수준 위치({@code "0"} 부터)를 체계의 수준 순서로 되찾는다. 체계에 없는 여분의
 * 답은 읽지 않는다 — 원본 응답째 저장된다. 응답의 모양은 기사와 무관하므로 읽을 수 없으면 평가기 사용 불가다.
 */
public final class JevResponse {

	/** 평가기 이름 */
	public static final String EVALUATOR = "jev";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private JevResponse() {
	}

	/**
	 * @return 넘긴 체계의 모든 질문에 기대한 종류의 답과 0 ~ 1 의 유한 확률이 있으면 {@link Evaluation}, 아니면 평가기 사용 불가 실패 —
	 * 문구는 첫 문제 하나(체계 키만)와 요청 ID
	 */
	public static EvaluationResult read(String body, @Nullable String requestId, TagSetView tagSet) {
		try {
			return evaluation(body, tagSet);
		}
		catch (Unreadable ex) {
			return new EvaluationFailure(EvaluatorFailureKind.EVALUATOR_UNAVAILABLE,
					JevErrors.clean(JevErrors.withRequestId("Jev 응답을 읽을 수 없다 — " + ex.getMessage(), requestId),
							JevErrors.MAX_MESSAGE_CODE_POINTS));
		}
	}

	private static Evaluation evaluation(String body, TagSetView tagSet) {
		JsonNode root;
		try {
			root = JSON.readTree(body);
		}
		catch (JacksonException ex) {
			throw new Unreadable("JSON 이 아니다");
		}
		if (root == null || !root.isObject()) {
			throw new Unreadable("JSON 객체가 아니다");
		}
		JsonNode model = root.get("model");
		if (model == null || !model.isString() || model.stringValue().isBlank()) {
			throw new Unreadable("model 이 없거나 비었다");
		}
		JsonNode usage = root.get("usage");
		JsonNode tokens = (usage != null && usage.isObject()) ? usage.get("input_tokens") : null;
		if (tokens == null || !tokens.isIntegralNumber() || !tokens.canConvertToInt() || tokens.intValue() < 0) {
			throw new Unreadable("usage.input_tokens 가 0 이상의 정수가 아니다");
		}
		JsonNode answers = root.get("answers");
		if (answers == null || !answers.isObject()) {
			throw new Unreadable("answers 가 없다");
		}
		Map<String, Double> topics = new LinkedHashMap<>();
		for (TopicCategory category : tagSet.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				topics.put(subtopic.key(), yes(answers, JevRequest.TOPIC_PREFIX + subtopic.key()));
			}
		}
		Map<String, Double> regions = new LinkedHashMap<>();
		for (AskedTag region : tagSet.regions()) {
			regions.put(region.key(), yes(answers, JevRequest.REGION_PREFIX + region.key()));
		}
		JsonNode format = probabilities(answers, JevRequest.FORMAT, "choice");
		Map<String, Double> formats = new LinkedHashMap<>();
		for (FormatOption option : tagSet.format().options()) {
			formats.put(option.key(), probability(format.get(option.key()), JevRequest.FORMAT + "." + option.key()));
		}
		JsonNode importance = probabilities(answers, JevRequest.IMPORTANCE, "score");
		List<ImportanceLevel> levels = tagSet.importance().levels();
		Map<Integer, Double> importanceByLevel = new LinkedHashMap<>();
		for (int position = 0; position < levels.size(); position++) {
			importanceByLevel.put(levels.get(position).level(), probability(
					importance.get(Integer.toString(position)), JevRequest.IMPORTANCE + "." + position));
		}
		return new Evaluation(topics, regions, formats, importanceByLevel, EVALUATOR, model.stringValue(),
				tokens.intValue(), body);
	}

	private static double yes(JsonNode answers, String name) {
		return probability(answer(answers, name, "noul").get("noul"), name);
	}

	private static JsonNode probabilities(JsonNode answers, String name, String type) {
		JsonNode probabilities = answer(answers, name, type).get("probabilities");
		if (probabilities == null || !probabilities.isObject()) {
			throw new Unreadable("확률 분포가 없다 — " + name);
		}
		return probabilities;
	}

	private static JsonNode answer(JsonNode answers, String name, String type) {
		JsonNode answer = answers.get(name);
		if (answer == null || !answer.isObject()) {
			throw new Unreadable("답이 없다 — " + name);
		}
		JsonNode actual = answer.get("type");
		if (actual == null || !actual.isString() || !type.equals(actual.stringValue())) {
			throw new Unreadable("답의 종류가 다르다 (기대 " + type + ") — " + name);
		}
		return answer;
	}

	private static double probability(@Nullable JsonNode node, String where) {
		if (node == null || !node.isNumber()) {
			throw new Unreadable("확률이 없거나 숫자가 아니다 — " + where);
		}
		double value = node.doubleValue();
		if (!Double.isFinite(value) || value < 0 || value > 1) {
			throw new Unreadable("확률이 0 ~ 1 이 아니다 — " + where);
		}
		return value;
	}

	/** 응답을 읽을 수 없는 까닭 — 문구는 체계 키와 고정 문구만 담는다 */
	private static final class Unreadable extends RuntimeException {

		Unreadable(String message) {
			super(message, null, false, false);
		}

	}

}
