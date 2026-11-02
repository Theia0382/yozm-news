package net.yozmnews.evaluation.domain.tagset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.FormatDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.LevelDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.QuestionsDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.TagDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.ThresholdDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.ThresholdsDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.TopicDraft;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
import org.jspecify.annotations.Nullable;

/**
 * 초안 → 태그 체계 정의 — docs/specs/evaluation.md §4. 키 · 구성 · 문구 · 두 기준 규칙의 정본이다(evaluation-R6 ~ R8 ·
 * evaluation-R10).
 * <p>
 * 문제는 모두 모은다. 하나라도 있으면 정의를 만들지 않는다.
 */
final class TagSetValidator {

	/** 이름의 길이 상한(문자 수) — evaluation-R8 */
	static final int NAME_MAX_LENGTH = 40;

	/** 설명 · 중요도 기준의 길이 상한(문자 수) — evaluation-R8 */
	static final int DESCRIPTION_MAX_LENGTH = 120;

	/** 질문(틀 포함)의 길이 상한(문자 수) — evaluation-R8 */
	static final int QUESTION_MAX_LENGTH = 200;

	/** 형식 선택지 수의 하한 — evaluation-R7 */
	static final int MIN_FORMATS = 2;

	/** 중요도 수준 — evaluation-R7 */
	static final int MIN_LEVEL = 1;

	static final int MAX_LEVEL = 5;

	private final List<TagSetProblem> problems = new ArrayList<>();

	private TagSetValidator() {
	}

	static ValidationResult validate(TagSetDraft draft) {
		TagSetValidator validator = new TagSetValidator();
		TagSetDefinition definition = validator.definition(draft);
		if (!validator.problems.isEmpty()) {
			return new ValidationResult.Invalid(validator.problems);
		}
		return new ValidationResult.Valid(Objects.requireNonNull(definition));
	}

	/** 검증 결과 */
	sealed interface ValidationResult {

		/** 검증을 통과했다 */
		record Valid(TagSetDefinition definition) implements ValidationResult {
		}

		/** 문제가 있다 */
		record Invalid(List<TagSetProblem> problems) implements ValidationResult {

			public Invalid {
				problems = List.copyOf(problems);
			}

		}

	}

	private @Nullable TagSetDefinition definition(TagSetDraft draft) {
		YamlPath root = YamlPath.ROOT;
		Questions questions = questions(draft.questions(), root.child(TagSetBinder.QUESTIONS), draft.location());
		Map<MultiTagKind, ThresholdValues> thresholds = thresholds(draft.thresholds(),
				root.child(TagSetBinder.THRESHOLDS), draft.location());
		List<TopicCategoryDef> topics = topics(draft.topics(), root.child(TagSetBinder.TOPICS), draft.location());
		List<TagDef> regions = regions(draft.regions(), root.child(TagSetBinder.REGIONS), draft.location());
		List<FormatDef> formats = formats(draft.formats(), root.child(TagSetBinder.FORMATS), draft.location());
		List<ImportanceLevelDef> importance = importance(draft.importance(), root.child(TagSetBinder.IMPORTANCE),
				draft.location());
		if (!this.problems.isEmpty() || questions == null) {
			return null;
		}
		return new TagSetDefinition(new TagSetContent(questions.topic(), questions.region(), questions.format(),
				questions.importance(), topics, regions, formats, importance), thresholds);
	}

	private record Questions(QuestionTemplate topic, QuestionTemplate region, String format, String importance) {
	}

	private @Nullable Questions questions(@Nullable QuestionsDraft draft, YamlPath path, TextLocation parent) {
		if (draft == null) {
			missing(path, parent);
			return null;
		}
		QuestionTemplate topic = template(draft.topic(), path.child(TagSetBinder.TOPIC), draft.location());
		QuestionTemplate region = template(draft.region(), path.child(TagSetBinder.REGION), draft.location());
		String format = question(draft.format(), path.child(TagSetBinder.FORMAT), draft.location());
		String importance = question(draft.importance(), path.child(TagSetBinder.IMPORTANCE), draft.location());
		if (topic == null || region == null || format == null || importance == null) {
			return null;
		}
		return new Questions(topic, region, format, importance);
	}

	/** 주제 · 지역 질문 틀 — 자리표시자가 정확히 하나 */
	private @Nullable QuestionTemplate template(@Nullable Located<String> value, YamlPath path, TextLocation parent) {
		String text = text(value, path, parent, QUESTION_MAX_LENGTH);
		if (text == null) {
			return null;
		}
		int placeholders = QuestionTemplate.placeholders(text);
		if (placeholders != 1) {
			problem(path, TagSetProblemCode.INVALID_TEMPLATE, "질문 틀은 자리표시자 " + QuestionTemplate.PLACEHOLDER
					+ " 를 정확히 하나 갖는다 — 지금 " + placeholders + "개", Objects.requireNonNull(value).location());
			return null;
		}
		return new QuestionTemplate(text);
	}

	/** 형식 · 중요도 질문 — 자리표시자가 없다 */
	private @Nullable String question(@Nullable Located<String> value, YamlPath path, TextLocation parent) {
		String text = text(value, path, parent, QUESTION_MAX_LENGTH);
		if (text == null) {
			return null;
		}
		if (QuestionTemplate.placeholders(text) > 0) {
			problem(path, TagSetProblemCode.PLACEHOLDER_NOT_ALLOWED,
					"이 질문에는 자리표시자 " + QuestionTemplate.PLACEHOLDER + " 를 쓰지 않는다", Objects.requireNonNull(value).location());
			return null;
		}
		return text;
	}

	private Map<MultiTagKind, ThresholdValues> thresholds(@Nullable ThresholdsDraft draft, YamlPath path,
			TextLocation parent) {
		Map<MultiTagKind, ThresholdValues> thresholds = new EnumMap<>(MultiTagKind.class);
		if (draft == null) {
			missing(path, parent);
			return thresholds;
		}
		for (MultiTagKind kind : MultiTagKind.values()) {
			String key = switch (kind) {
				case TOPIC -> TagSetBinder.TOPIC;
				case REGION -> TagSetBinder.REGION;
			};
			ThresholdDraft threshold = switch (kind) {
				case TOPIC -> draft.topic();
				case REGION -> draft.region();
			};
			ThresholdValues values = threshold(threshold, path.child(key), draft.location());
			if (values != null) {
				thresholds.put(kind, values);
			}
		}
		return thresholds;
	}

	private @Nullable ThresholdValues threshold(@Nullable ThresholdDraft draft, YamlPath path, TextLocation parent) {
		if (draft == null) {
			missing(path, parent);
			return null;
		}
		BigDecimal tag = thresholdValue(draft.tagThreshold(), path.child(TagSetBinder.TAG_THRESHOLD), draft.location());
		BigDecimal store = thresholdValue(draft.storeThreshold(), path.child(TagSetBinder.STORE_THRESHOLD),
				draft.location());
		if (tag == null || store == null) {
			return null;
		}
		return new ThresholdValues(tag, store);
	}

	private @Nullable BigDecimal thresholdValue(@Nullable Located<BigDecimal> value, YamlPath path, TextLocation parent) {
		if (value == null) {
			missing(path, parent);
			return null;
		}
		if (!ThresholdValues.valid(value.value())) {
			problem(path, TagSetProblemCode.INVALID_THRESHOLD,
					"기준은 0 이상 1 이하, 소수 셋째 자리까지다 — 지금 " + value.value().toPlainString(), value.location());
			return null;
		}
		return value.value();
	}

	private List<TopicCategoryDef> topics(@Nullable Located<List<TopicDraft>> draft, YamlPath path,
			TextLocation parent) {
		List<TopicCategoryDef> topics = new ArrayList<>();
		if (draft == null) {
			missing(path, parent);
			return topics;
		}
		if (draft.value().isEmpty()) {
			problem(path, TagSetProblemCode.NO_TOPICS, "주제 대분류가 하나도 없다", draft.location());
			return topics;
		}
		Set<String> keys = new HashSet<>();
		for (TopicDraft topic : draft.value()) {
			TagKey key = key(topic.key(), topic.path().child(TagSetBinder.KEY), topic.location(), keys, "대분류");
			String name = text(topic.name(), topic.path().child(TagSetBinder.NAME), topic.location(), NAME_MAX_LENGTH);
			String description = text(topic.description(), topic.path().child(TagSetBinder.DESCRIPTION),
					topic.location(), DESCRIPTION_MAX_LENGTH);
			List<TagDef> subtopics = subtopics(topic, key);
			if (key != null && name != null && description != null) {
				topics.add(new TopicCategoryDef(key, name, description, subtopics));
			}
		}
		return topics;
	}

	private List<TagDef> subtopics(TopicDraft topic, @Nullable TagKey parent) {
		YamlPath path = topic.path().child(TagSetBinder.SUBTOPICS);
		List<TagDef> subtopics = new ArrayList<>();
		Located<List<TagDraft>> draft = topic.subtopics();
		if (draft == null) {
			missing(path, topic.location());
			return subtopics;
		}
		if (draft.value().isEmpty()) {
			problem(path, TagSetProblemCode.NO_SUBTOPICS, "대분류에 중분류가 하나도 없다", draft.location());
			return subtopics;
		}
		Set<String> keys = new HashSet<>();
		for (TagDraft subtopic : draft.value()) {
			TagKey segment = key(subtopic.key(), subtopic.path().child(TagSetBinder.KEY), subtopic.location(), keys,
					"같은 대분류의 중분류");
			TagDef def = tag(subtopic, segment);
			if (def != null && parent != null) {
				subtopics.add(new TagDef(TagKey.child(parent, segment.value()), def.name(), def.description()));
			}
		}
		return subtopics;
	}

	private List<TagDef> regions(@Nullable Located<List<TagDraft>> draft, YamlPath path, TextLocation parent) {
		List<TagDef> regions = new ArrayList<>();
		if (draft == null) {
			missing(path, parent);
			return regions;
		}
		if (draft.value().isEmpty()) {
			problem(path, TagSetProblemCode.NO_REGIONS, "지역이 하나도 없다", draft.location());
			return regions;
		}
		Set<String> keys = new HashSet<>();
		for (TagDraft region : draft.value()) {
			TagKey key = key(region.key(), region.path().child(TagSetBinder.KEY), region.location(), keys, "지역");
			TagDef def = tag(region, key);
			if (def != null) {
				regions.add(def);
			}
		}
		return regions;
	}

	/** 키를 뺀 태그의 이름 · 설명을 검증한다 — 키가 쓸 수 없으면 정의는 null 이지만 이름 · 설명의 문제는 함께 모은다 */
	private @Nullable TagDef tag(TagDraft draft, @Nullable TagKey key) {
		String name = text(draft.name(), draft.path().child(TagSetBinder.NAME), draft.location(), NAME_MAX_LENGTH);
		String description = text(draft.description(), draft.path().child(TagSetBinder.DESCRIPTION), draft.location(),
				DESCRIPTION_MAX_LENGTH);
		if (key == null || name == null || description == null) {
			return null;
		}
		return new TagDef(key, name, description);
	}

	private List<FormatDef> formats(@Nullable Located<List<FormatDraft>> draft, YamlPath path, TextLocation parent) {
		List<FormatDef> formats = new ArrayList<>();
		if (draft == null) {
			missing(path, parent);
			return formats;
		}
		if (draft.value().size() < MIN_FORMATS) {
			problem(path, TagSetProblemCode.TOO_FEW_FORMATS,
					"형식은 " + MIN_FORMATS + "개 이상이어야 한다 — 지금 " + draft.value().size() + "개", draft.location());
		}
		Set<String> keys = new HashSet<>();
		for (FormatDraft format : draft.value()) {
			TagKey key = key(format.key(), format.path().child(TagSetBinder.KEY), format.location(), keys, "형식");
			String name = text(format.name(), format.path().child(TagSetBinder.NAME), format.location(),
					NAME_MAX_LENGTH);
			String description = text(format.description(), format.path().child(TagSetBinder.DESCRIPTION),
					format.location(), DESCRIPTION_MAX_LENGTH);
			Located<Boolean> excluded = format.excludedByDefault();
			if (key != null && name != null && description != null) {
				formats.add(new FormatDef(key, name, description, excluded != null && excluded.value()));
			}
		}
		return formats;
	}

	private List<ImportanceLevelDef> importance(@Nullable Located<List<LevelDraft>> draft, YamlPath path,
			TextLocation parent) {
		if (draft == null) {
			missing(path, parent);
			return List.of();
		}
		Map<Integer, ImportanceLevelDef> levels = new TreeMap<>();
		Set<Integer> seen = new HashSet<>();
		boolean valid = draft.value().size() == MAX_LEVEL - MIN_LEVEL + 1;
		for (LevelDraft level : draft.value()) {
			Located<Integer> number = level.level();
			if (number == null) {
				missing(level.path().child(TagSetBinder.LEVEL), level.location());
			}
			String description = text(level.description(), level.path().child(TagSetBinder.DESCRIPTION),
					level.location(), DESCRIPTION_MAX_LENGTH);
			if (number == null) {
				continue;
			}
			int value = number.value();
			if (value < MIN_LEVEL || value > MAX_LEVEL || !seen.add(value)) {
				valid = false;
			}
			if (description != null) {
				levels.putIfAbsent(value, new ImportanceLevelDef(value, description));
			}
		}
		if (!valid) {
			problem(path, TagSetProblemCode.INVALID_IMPORTANCE_LEVELS,
					"중요도 수준은 " + MIN_LEVEL + " ~ " + MAX_LEVEL + " 가 하나씩이어야 한다", draft.location());
		}
		return List.copyOf(levels.values());
	}

	/**
	 * 키 조각을 검증하고 같은 태그 종류 안의 겹침을 본다.
	 * @param seen 이 범위에서 이미 나온 조각 — 쓸 수 있는 조각이면 더한다
	 * @param scope 문구에 쓸 범위 이름
	 * @return 조각 하나의 키 — 쓸 수 없으면 null
	 */
	private @Nullable TagKey key(@Nullable Located<String> value, YamlPath path, TextLocation parent, Set<String> seen,
			String scope) {
		if (value == null) {
			missing(path, parent);
			return null;
		}
		String segment = value.value();
		if (!TagKey.isSegment(segment)) {
			problem(path, TagSetProblemCode.INVALID_KEY, "태그 키 '" + segment + "' 는 영문 소문자 · 숫자를 하이픈 하나로 이은 "
					+ TagKey.MAX_SEGMENT_LENGTH + "자 이하여야 한다", value.location());
			return null;
		}
		if (!seen.add(segment)) {
			problem(path, TagSetProblemCode.DUPLICATE_KEY_IN_KIND, scope + " 키 '" + segment + "' 가 앞에 또 있다",
					value.location());
			return null;
		}
		return TagKey.of(segment);
	}

	/**
	 * 문구 하나를 검증한다 — 있어야 하고, 공백뿐이 아니고, 한 줄이고, 상한 이하.
	 * @return 쓸 수 없으면 null
	 */
	private @Nullable String text(@Nullable Located<String> value, YamlPath path, TextLocation parent, int maxLength) {
		if (value == null) {
			missing(path, parent);
			return null;
		}
		String text = value.value();
		if (text.isBlank()) {
			problem(path, TagSetProblemCode.BLANK, "문구가 공백뿐이다", value.location());
			return null;
		}
		if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
			problem(path, TagSetProblemCode.MULTI_LINE, "문구는 한 줄이어야 한다", value.location());
			return null;
		}
		int length = text.codePointCount(0, text.length());
		if (length > maxLength) {
			problem(path, TagSetProblemCode.TOO_LONG, "문구는 " + maxLength + "자까지다 — 지금 " + length + "자",
					value.location());
			return null;
		}
		return text;
	}

	private void missing(YamlPath path, TextLocation parent) {
		problem(path, TagSetProblemCode.MISSING_REQUIRED, "필수 항목이 없다", parent);
	}

	private void problem(YamlPath path, TagSetProblemCode code, String message, TextLocation location) {
		this.problems.add(new TagSetProblem(path, code, message, location));
	}

}
