package net.yozmnews.evaluation.domain.tagset;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.yozmnews.evaluation.LineageKind;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.FormatDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.LevelDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.LineageDraft;
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
 * 초안 → 태그 체계 정의 — docs/specs/evaluation.md §4. 키 · 구성 · 문구 · 태그 기준 · 저장 기준 · 원문 안 계보 규칙의 정본이다
 * (evaluation-R6 ~ R8 · evaluation-R10 · evaluation-R22).
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
			lineage(draft.lineage(), null);
			return null;
		}
		TagSetContent content = new TagSetContent(questions.topic(), questions.region(), questions.format(),
				questions.importance(), topics, regions, formats, importance);
		List<Located<LineageDef>> lineage = lineage(draft.lineage(), content);
		if (!this.problems.isEmpty()) {
			return null;
		}
		return new TagSetDefinition(content, thresholds, lineage);
	}

	/**
	 * 계보 절을 원문 하나 안에서 검증한다(evaluation-R22).
	 * @param content 이 원문의 내용 — 다른 항목에 문제가 있어 없으면 새 키가 원문에 있는지는 보지 않는다(문제가 연쇄로 불지 않게)
	 * @return 쓸 수 있는 계보 항목 — 원문 순서
	 */
	private List<Located<LineageDef>> lineage(@Nullable Located<List<LineageDraft>> draft,
			@Nullable TagSetContent content) {
		List<Located<LineageDef>> lineage = new ArrayList<>();
		if (draft == null) {
			return lineage;
		}
		Map<TagKind, Set<String>> fromSeen = new EnumMap<>(TagKind.class);
		Map<TagKind, Set<String>> toSeen = new EnumMap<>(TagKind.class);
		for (LineageDraft item : draft.value()) {
			TagKind kind = named(item.kind(), item.path().child(TagSetBinder.KIND), item.location(), TagKind.values(),
					TagSetValidator::kindName, "태그 종류");
			LineageKind change = named(item.change(), item.path().child(TagSetBinder.CHANGE), item.location(),
					LineageKind.values(), TagSetValidator::lineageName, "계보 종류");
			List<Located<TagKey>> from = lineageKeys(item.from(), item.path().child(TagSetBinder.FROM), item.location(),
					kind);
			List<Located<TagKey>> to = lineageKeys(item.to(), item.path().child(TagSetBinder.TO), item.location(),
					kind);
			if (kind == null || change == null || from == null || to == null) {
				continue;
			}
			if (!countsFit(change, from.size(), to.size())) {
				problem(item.path(), TagSetProblemCode.INVALID_LINEAGE_COUNT, lineageName(change)
						+ " 는 이전 키 · 새 키가 " + countRule(change) + "이어야 한다 — 지금 " + from.size() + " → " + to.size(),
						item.location());
				continue;
			}
			List<TagKey> fromKeys = from.stream().map(Located::value).toList();
			List<TagKey> toKeys = to.stream().map(Located::value).toList();
			if (change == LineageKind.RENAME && fromKeys.equals(toKeys)) {
				problem(item.path(), TagSetProblemCode.DUPLICATE_LINEAGE_KEY, "이름 변경의 이전 키와 새 키가 같다",
						item.location());
				continue;
			}
			if (kind == TagKind.TOPIC && !sameLayer(fromKeys, toKeys)) {
				problem(item.path(), TagSetProblemCode.LINEAGE_LAYER_MISMATCH,
						"주제 계보는 대분류는 대분류로, 중분류는 중분류로만 잇는다", item.location());
				continue;
			}
			boolean usable = repeatsNone(from, fromSeen.computeIfAbsent(kind, k -> new HashSet<>()), "이전 키");
			usable &= repeatsNone(to, toSeen.computeIfAbsent(kind, k -> new HashSet<>()), "새 키");
			if (content != null) {
				Set<String> keys = new HashSet<>(content.keys(kind));
				for (Located<TagKey> key : to) {
					if (!keys.contains(key.value().value())) {
						problem(key.path(), TagSetProblemCode.LINEAGE_TARGET_MISSING,
								"새 키 '" + key.value() + "' 가 이 원문의 " + kindName(kind) + " 에 없다", key.location());
						usable = false;
					}
				}
			}
			if (usable) {
				lineage.add(
						new Located<>(new LineageDef(kind, change, fromKeys, toKeys), item.path(), item.location()));
			}
		}
		return lineage;
	}

	/**
	 * 원문의 이름 값을 열거값으로 읽는다 — 값이 없으면 missing, 맞는 이름이 없으면 INVALID_LINEAGE_VALUE. 허용 이름 목록은 열거 선언
	 * 순으로 {@code name} 에서 만든다.
	 * @param what 문구의 대상 — "태그 종류" · "계보 종류"
	 */
	private <E extends Enum<E>> @Nullable E named(@Nullable Located<String> value, YamlPath path, TextLocation parent,
			E[] values, Function<E, String> name, String what) {
		if (value == null) {
			missing(path, parent);
			return null;
		}
		for (E candidate : values) {
			if (name.apply(candidate).equals(value.value())) {
				return candidate;
			}
		}
		problem(path, TagSetProblemCode.INVALID_LINEAGE_VALUE,
				what + "는 " + Arrays.stream(values).map(name).collect(Collectors.joining(" · ")) + " 가운데 하나다 — 지금 '"
						+ value.value() + "'",
				value.location());
		return null;
	}

	/**
	 * 계보 항목의 키 목록 — 키 형식과 한 목록 안의 겹침을 본다. 지역 · 형식의 키는 조각 하나다.
	 * @param kind 그 항목의 태그 종류 — 쓸 수 없으면 null(조각 수는 보지 않는다)
	 * @return 쓸 수 없는 키가 있거나 목록이 없으면 null
	 */
	private @Nullable List<Located<TagKey>> lineageKeys(@Nullable Located<List<Located<String>>> value, YamlPath path,
			TextLocation parent, @Nullable TagKind kind) {
		if (value == null) {
			missing(path, parent);
			return null;
		}
		List<Located<TagKey>> keys = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		boolean usable = true;
		for (Located<String> item : value.value()) {
			String text = item.value();
			boolean single = kind == null || kind == TagKind.TOPIC || TagKey.isSegment(text);
			if (!TagKey.isKey(text) || !single) {
				problem(item.path(), TagSetProblemCode.INVALID_KEY, "계보의 키 '" + text + "' 가 "
						+ ((kind == null || kind == TagKind.TOPIC) ? "태그 키" : kindName(kind) + " 키(조각 하나)")
						+ " 형식이 아니다", item.location());
				usable = false;
				continue;
			}
			if (!seen.add(text)) {
				problem(item.path(), TagSetProblemCode.DUPLICATE_LINEAGE_KEY, "키 '" + text + "' 가 이 목록에 또 있다",
						item.location());
				usable = false;
				continue;
			}
			keys.add(new Located<>(new TagKey(text), item.path(), item.location()));
		}
		return usable ? keys : null;
	}

	/** 같은 태그 종류에서 한 키는 많아야 한 항목의 이전 키(새 키)다 — 겹치면 알린다 */
	private boolean repeatsNone(List<Located<TagKey>> keys, Set<String> seen, String role) {
		boolean none = true;
		for (Located<TagKey> key : keys) {
			if (!seen.add(key.value().value())) {
				problem(key.path(), TagSetProblemCode.LINEAGE_KEY_REPEATED,
						role + " '" + key.value() + "' 가 앞의 계보 항목에도 있다", key.location());
				none = false;
			}
		}
		return none;
	}

	/** 주제 계보의 키가 모두 대분류이거나 모두 중분류인가 */
	private static boolean sameLayer(List<TagKey> from, List<TagKey> to) {
		boolean child = from.get(0).isChild();
		return from.stream().allMatch(key -> key.isChild() == child)
				&& to.stream().allMatch(key -> key.isChild() == child);
	}

	private static boolean countsFit(LineageKind change, int from, int to) {
		return switch (change) {
			case RENAME -> from == 1 && to == 1;
			case SPLIT -> from == 1 && to >= 2;
			case MERGE -> from >= 2 && to == 1;
		};
	}

	private static String countRule(LineageKind change) {
		return switch (change) {
			case RENAME -> "하나 → 하나";
			case SPLIT -> "하나 → 둘 이상";
			case MERGE -> "둘 이상 → 하나";
		};
	}

	/** 원문에 적는 태그 종류 이름 */
	static String kindName(TagKind kind) {
		return switch (kind) {
			case TOPIC -> TagSetBinder.TOPIC;
			case REGION -> TagSetBinder.REGION;
			case FORMAT -> TagSetBinder.FORMAT;
		};
	}

	/** 기준이 있는 태그 종류의 태그 종류 */
	private static TagKind tagKind(MultiTagKind kind) {
		return switch (kind) {
			case TOPIC -> TagKind.TOPIC;
			case REGION -> TagKind.REGION;
		};
	}

	/** 원문에 적는 계보 종류 이름 */
	static String lineageName(LineageKind change) {
		return switch (change) {
			case RENAME -> TagSetBinder.RENAME;
			case SPLIT -> TagSetBinder.SPLIT;
			case MERGE -> TagSetBinder.MERGE;
		};
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
			String key = kindName(tagKind(kind));
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
