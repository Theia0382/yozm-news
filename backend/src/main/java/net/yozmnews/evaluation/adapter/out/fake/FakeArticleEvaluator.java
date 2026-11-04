package net.yozmnews.evaluation.adapter.out.fake;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고정 규칙 평가기 — 로컬 대체물 · docs/specs/evaluation.md §4.
 * <p>
 * 외부 호출 없이 같은 입력에 같은 결과를 낸다. 조종 계약(실패 표지 · 이름 일치가 보는 입력 · 이름 · 버전)은 Spec 이 정본이다.
 * <p>
 * 이 클래스가 정본인 내부 규칙:
 * <ul>
 * <li>이름 일치 — 태그 · 대분류 이름을 {@code ·} 로 나눠 앞뒤 공백을 지운 조각 가운데 2자 이상인 것 하나가 일치 문자열에
 * 들어 있으면 일치한다</li>
 * <li>주제 — 중분류 이름이 일치하면 그 중분류 {@value #NAME_MATCH}. 대분류 이름이 일치하고 그 대분류에 일치한 중분류가 없으면 그
 * 대분류의 중분류 가운데 해시로 고른 하나 {@value #CATEGORY_MATCH}. 둘 다 없으면 전체 중분류 가운데 해시로 고른 하나
 * {@value #PICKED}. 나머지는 {@value #LOW_BOUND} 미만의 해시값</li>
 * <li>지역 — 이름이 일치하면 {@value #NAME_MATCH}, 하나도 없으면 체계 순서의 첫 지역 {@value #PICKED}, 나머지는
 * {@value #LOW_BOUND} 미만의 해시값</li>
 * <li>형식 — 이름이 일치한 첫 선택지, 없으면 체계 순서의 첫 선택지가 {@value #FORMAT_CHOSEN}, 나머지 선택지가 그 나머지를
 * 똑같이 나눈다</li>
 * <li>중요도 — 해시로 고른 수준이 {@value #IMPORTANCE_CHOSEN}, 나머지 수준이 그 나머지를 똑같이 나눈다</li>
 * <li>해시 — {@code SHA-256(제목 + "\n" + 구분자 + "\n" + 키)} 의 앞 8바이트를 0 이상 1 미만으로 옮긴 값. 해시로 고르면 해시값이
 * 가장 큰 키(같으면 체계 순서가 앞선 키)다</li>
 * <li>입력 토큰 — 제목 · 요약 · 출처 이름 · 섹션 · 카테고리와 체계의 질문 문장 · 형식 선택지 이름 · 중요도 기준의 코드 포인트 합을
 * 2로 나눠 올린 값. 실제 토큰 수가 아니다</li>
 * <li>원본 응답 — 평가기 이름 · 버전과 태그 종류별 확률을 키 순으로 담은 JSON. 다시 읽기는 넘긴 체계의 키마다 그 확률을 읽고,
 * 입력 토큰은 원본에 없어 0 이다</li>
 * </ul>
 * 넘긴 체계는 검증된 체계다 — 주제 · 중분류 · 지역이 하나 이상, 형식 선택지가 둘 이상이다.
 */
public final class FakeArticleEvaluator implements ArticleEvaluator {

	/** 평가기 이름 */
	public static final String NAME = "fake";

	/** 모델 버전 — 규칙을 바꾸면 올린다 */
	public static final String MODEL_VERSION = "fake-rules-2";

	/** 원본 응답의 키 — 쓰기와 다시 읽기가 함께 쓴다 */
	private static final String RAW_EVALUATOR = "evaluator";

	private static final String RAW_MODEL_VERSION = "modelVersion";

	private static final String RAW_TOPICS = "topics";

	private static final String RAW_REGIONS = "regions";

	private static final String RAW_FORMATS = "formats";

	private static final String RAW_IMPORTANCE = "importance";

	/** 이름이 일치한 태그의 확률 */
	static final double NAME_MATCH = 0.9;

	/** 대분류 이름만 일치했을 때 고른 중분류의 확률 */
	static final double CATEGORY_MATCH = 0.8;

	/** 일치가 없을 때 고른 주제 · 지역의 확률 */
	static final double PICKED = 0.7;

	/** 나머지 태그 확률의 상한(미만) — 저장 기준 기본값(0.1)을 조금 넘겨, 나머지 가운데 소수만 저장 기준 이상이 되게 한다 */
	static final double LOW_BOUND = 0.12;

	/** 고른 형식의 확률 */
	static final double FORMAT_CHOSEN = 0.8;

	/** 고른 중요도 수준의 확률 */
	static final double IMPORTANCE_CHOSEN = 0.6;

	/** 실패 표지에 따른 실패의 문구 */
	static final String FAILURE_MESSAGE = "Fake 평가기 표지에 따른 실패";

	private static final String NAME_SEPARATOR = "·";

	private static final int MIN_PIECE_CODE_POINTS = 2;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(tagSet, "tagSet");
		String title = input.title();
		for (EvaluationFailure.Kind kind : EvaluationFailure.Kind.values()) {
			if (title.contains(marker(kind))) {
				return new EvaluationFailure(kind, FAILURE_MESSAGE);
			}
		}
		String text = matchText(input);
		Map<String, Double> topics = topics(title, tagSet.topics(), text);
		Map<String, Double> regions = regions(title, tagSet.regions(), text);
		Map<String, Double> formats = formats(tagSet.format().options(), text);
		Map<Integer, Double> importance = importance(title, tagSet.importance().levels());
		return new Evaluation(topics, regions, formats, importance, NAME, MODEL_VERSION, tokens(input, tagSet),
				rawResponse(topics, regions, formats, importance));
	}

	/** 자기가 남긴 원본 응답을 넘긴 체계로 다시 읽는다 */
	@Override
	public RereadResult reread(String evaluator, String rawResponse, TagSetView tagSet) {
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(tagSet, "tagSet");
		if (!NAME.equals(evaluator)) {
			return new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "Fake 가 남긴 원본 응답이 아니다 — 평가기 " + evaluator);
		}
		try {
			return reread(rawResponse, tagSet);
		}
		catch (Unreadable ex) {
			return new RereadFailure(RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — " + ex.getMessage());
		}
	}

	/** 실패 갈래의 표지 — docs/specs/evaluation.md §4 */
	static String marker(EvaluationFailure.Kind kind) {
		String name = switch (kind) {
			case TRANSIENT -> "transient";
			case ARTICLE_REJECTED -> "article-rejected";
			case EVALUATOR_UNAVAILABLE -> "evaluator-unavailable";
		};
		return "[fake:" + name + "]";
	}

	/** 이름 일치를 볼 문자열 — 보는 입력은 docs/specs/evaluation.md §4 */
	private static String matchText(EvaluationInput input) {
		List<String> parts = new ArrayList<>();
		parts.add(input.title());
		for (FeedContext feed : input.feeds()) {
			if (feed.section() != null) {
				parts.add(feed.section());
			}
			parts.addAll(feed.categories());
		}
		return String.join("\n", parts);
	}

	private static boolean matches(String name, String text) {
		for (String piece : name.split(NAME_SEPARATOR)) {
			String stripped = piece.strip();
			if (stripped.codePointCount(0, stripped.length()) >= MIN_PIECE_CODE_POINTS && text.contains(stripped)) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, Double> topics(String title, List<TopicCategory> categories, String text) {
		Map<String, Double> result = new LinkedHashMap<>();
		List<String> all = new ArrayList<>();
		for (TopicCategory category : categories) {
			for (AskedTag subtopic : category.subtopics()) {
				result.put(subtopic.key(), low(title, subtopic.key()));
				all.add(subtopic.key());
			}
		}
		boolean matched = false;
		for (TopicCategory category : categories) {
			boolean subtopicMatched = false;
			for (AskedTag subtopic : category.subtopics()) {
				if (matches(subtopic.name(), text)) {
					result.put(subtopic.key(), NAME_MATCH);
					subtopicMatched = true;
				}
			}
			if (!subtopicMatched && matches(category.name(), text)) {
				result.put(pick(title, "topic", category.subtopics().stream().map(AskedTag::key).toList()),
						CATEGORY_MATCH);
				matched = true;
			}
			matched |= subtopicMatched;
		}
		if (!matched) {
			result.put(pick(title, "topic", all), PICKED);
		}
		return result;
	}

	private static Map<String, Double> regions(String title, List<AskedTag> regions, String text) {
		Map<String, Double> result = new LinkedHashMap<>();
		boolean matched = false;
		for (AskedTag region : regions) {
			if (matches(region.name(), text)) {
				result.put(region.key(), NAME_MATCH);
				matched = true;
			}
			else {
				result.put(region.key(), low(title, region.key()));
			}
		}
		if (!matched) {
			result.put(regions.getFirst().key(), PICKED);
		}
		return result;
	}

	private static Map<String, Double> formats(List<FormatOption> options, String text) {
		String chosen = options.stream()
			.filter(option -> matches(option.name(), text))
			.map(FormatOption::key)
			.findFirst()
			.orElse(options.getFirst().key());
		double rest = (1 - FORMAT_CHOSEN) / (options.size() - 1);
		Map<String, Double> result = new LinkedHashMap<>();
		for (FormatOption option : options) {
			result.put(option.key(), option.key().equals(chosen) ? FORMAT_CHOSEN : rest);
		}
		return result;
	}

	private static Map<Integer, Double> importance(String title, List<ImportanceLevel> levels) {
		String chosen = pick(title, "importance",
				levels.stream().map(level -> Integer.toString(level.level())).toList());
		double rest = (1 - IMPORTANCE_CHOSEN) / (levels.size() - 1);
		Map<Integer, Double> result = new LinkedHashMap<>();
		for (ImportanceLevel level : levels) {
			result.put(level.level(), Integer.toString(level.level()).equals(chosen) ? IMPORTANCE_CHOSEN : rest);
		}
		return result;
	}

	private static int tokens(EvaluationInput input, TagSetView tagSet) {
		List<String> texts = new ArrayList<>();
		texts.add(input.title());
		if (input.summary() != null) {
			texts.add(input.summary());
		}
		for (FeedContext feed : input.feeds()) {
			if (feed.sourceName() != null) {
				texts.add(feed.sourceName());
			}
			if (feed.section() != null) {
				texts.add(feed.section());
			}
			texts.addAll(feed.categories());
		}
		tagSet.topics().forEach(category -> category.subtopics().forEach(subtopic -> texts.add(subtopic.question())));
		tagSet.regions().forEach(region -> texts.add(region.question()));
		texts.add(tagSet.format().question());
		tagSet.format().options().forEach(option -> texts.add(option.name()));
		texts.add(tagSet.importance().question());
		tagSet.importance().levels().forEach(level -> texts.add(level.description()));
		long codePoints = texts.stream().mapToLong(text -> text.codePointCount(0, text.length())).sum();
		return Math.toIntExact((codePoints + 1) / 2);
	}

	private static String rawResponse(Map<String, Double> topics, Map<String, Double> regions,
			Map<String, Double> formats, Map<Integer, Double> importance) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put(RAW_EVALUATOR, NAME);
		root.put(RAW_MODEL_VERSION, MODEL_VERSION);
		root.put(RAW_TOPICS, new TreeMap<>(topics));
		root.put(RAW_REGIONS, new TreeMap<>(regions));
		root.put(RAW_FORMATS, new TreeMap<>(formats));
		root.put(RAW_IMPORTANCE, new TreeMap<>(importance));
		return JSON.writeValueAsString(root);
	}

	/** 원본 응답에서 넘긴 체계의 키마다 확률을 읽는다 — 중요도 키는 수준의 문자열 */
	private static Evaluation reread(String rawResponse, TagSetView tagSet) {
		JsonNode root;
		try {
			root = JSON.readTree(rawResponse);
		}
		catch (JacksonException ex) {
			throw new Unreadable("JSON 이 아니다");
		}
		if (root == null || !root.isObject()) {
			throw new Unreadable("JSON 객체가 아니다");
		}
		JsonNode model = root.get(RAW_MODEL_VERSION);
		if (model == null || !model.isString() || model.stringValue().isBlank()) {
			throw new Unreadable(RAW_MODEL_VERSION + " 이 없거나 비었다");
		}
		Map<String, Double> topics = new LinkedHashMap<>();
		for (TopicCategory category : tagSet.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				topics.put(subtopic.key(), probability(root, RAW_TOPICS, subtopic.key()));
			}
		}
		Map<String, Double> regions = new LinkedHashMap<>();
		for (AskedTag region : tagSet.regions()) {
			regions.put(region.key(), probability(root, RAW_REGIONS, region.key()));
		}
		Map<String, Double> formats = new LinkedHashMap<>();
		for (FormatOption option : tagSet.format().options()) {
			formats.put(option.key(), probability(root, RAW_FORMATS, option.key()));
		}
		Map<Integer, Double> importance = new LinkedHashMap<>();
		for (ImportanceLevel level : tagSet.importance().levels()) {
			importance.put(level.level(), probability(root, RAW_IMPORTANCE, Integer.toString(level.level())));
		}
		// 입력 토큰은 원본 응답에 없다 — 다시 읽기는 토큰 수를 쓰지 않는다
		return new Evaluation(topics, regions, formats, importance, NAME, model.stringValue(), 0, rawResponse);
	}

	private static double probability(JsonNode root, String kind, String key) {
		JsonNode values = root.get(kind);
		JsonNode value = (values != null && values.isObject()) ? values.get(key) : null;
		if (value == null || !value.isNumber()) {
			throw new Unreadable("확률이 없거나 숫자가 아니다 — " + kind + "." + key);
		}
		double probability = value.doubleValue();
		if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
			throw new Unreadable("확률이 0 ~ 1 이 아니다 — " + kind + "." + key);
		}
		return probability;
	}

	/** 해시값이 가장 큰 키 — 같으면 앞선 키 */
	private static String pick(String title, String separator, List<String> keys) {
		String best = keys.getFirst();
		double bestValue = hash(title, separator, best);
		for (String key : keys.subList(1, keys.size())) {
			double value = hash(title, separator, key);
			if (value > bestValue) {
				best = key;
				bestValue = value;
			}
		}
		return best;
	}

	private static double low(String title, String key) {
		return hash(title, "low", key) * LOW_BOUND;
	}

	/** {@code SHA-256(제목 + "\n" + 구분자 + "\n" + 키)} 의 앞 8바이트 → 0 이상 1 미만 */
	private static double hash(String title, String separator, String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest((title + "\n" + separator + "\n" + key).getBytes(StandardCharsets.UTF_8));
			return (ByteBuffer.wrap(digest).getLong() >>> 11) * 0x1.0p-53;
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", ex);
		}
	}

	/** 원본 응답을 읽을 수 없는 까닭 — 문구는 체계 키와 고정 문구만 담는다 */
	private static final class Unreadable extends RuntimeException {

		Unreadable(String message) {
			super(message, null, false, false);
		}

	}

}
