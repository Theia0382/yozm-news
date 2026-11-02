package net.yozmnews.evaluation.domain.tagset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.evaluation.domain.tagset.TagSetDraft.FormatDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.LevelDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.LineageDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.QuestionsDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.TagDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.ThresholdDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.ThresholdsDraft;
import net.yozmnews.evaluation.domain.tagset.TagSetDraft.TopicDraft;
import net.yozmnews.platform.StrictYamlBinding;
import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.YamlPath;
import net.yozmnews.platform.YamlTree;
import net.yozmnews.platform.YamlTree.ListNode;
import net.yozmnews.platform.YamlTree.MapNode;
import org.jspecify.annotations.Nullable;

/**
 * 트리 → 초안 바인더 — docs/specs/evaluation.md §4 · evaluation-R5.
 * <p>
 * 태그 체계 파일 형식의 키 목록이 이 클래스에 있다. 공통 거부는 {@link StrictYamlBinding} 이 하고, 문제는 모두 한 목록에 찾은 순서대로
 * 모은다.
 */
final class TagSetBinder {

	static final String QUESTIONS = "questions";

	static final String THRESHOLDS = "thresholds";

	static final String TOPICS = "topics";

	static final String REGIONS = "regions";

	static final String FORMATS = "formats";

	static final String IMPORTANCE = "importance";

	static final String TOPIC = "topic";

	static final String REGION = "region";

	static final String FORMAT = "format";

	static final String TAG_THRESHOLD = "tagThreshold";

	static final String STORE_THRESHOLD = "storeThreshold";

	static final String KEY = "key";

	static final String NAME = "name";

	static final String DESCRIPTION = "description";

	static final String SUBTOPICS = "subtopics";

	static final String EXCLUDED_BY_DEFAULT = "excludedByDefault";

	static final String LEVEL = "level";

	static final String LINEAGE = "lineage";

	static final String KIND = "kind";

	static final String CHANGE = "change";

	static final String FROM = "from";

	static final String TO = "to";

	static final String RENAME = "rename";

	static final String SPLIT = "split";

	static final String MERGE = "merge";

	private static final Set<String> ROOT_KEYS = Set.of(QUESTIONS, THRESHOLDS, TOPICS, REGIONS, FORMATS, IMPORTANCE,
			LINEAGE);

	private static final Set<String> QUESTION_KEYS = Set.of(TOPIC, REGION, FORMAT, IMPORTANCE);

	private static final Set<String> THRESHOLD_KINDS = Set.of(TOPIC, REGION);

	private static final Set<String> THRESHOLD_KEYS = Set.of(TAG_THRESHOLD, STORE_THRESHOLD);

	private static final Set<String> TOPIC_KEYS = Set.of(KEY, NAME, DESCRIPTION, SUBTOPICS);

	private static final Set<String> TAG_KEYS = Set.of(KEY, NAME, DESCRIPTION);

	private static final Set<String> FORMAT_KEYS = Set.of(KEY, NAME, DESCRIPTION, EXCLUDED_BY_DEFAULT);

	private static final Set<String> LEVEL_KEYS = Set.of(LEVEL, DESCRIPTION);

	private static final Set<String> LINEAGE_KEYS = Set.of(KIND, CHANGE, FROM, TO);

	private final List<TagSetProblem> problems = new ArrayList<>();

	private final StrictYamlBinding binding = new StrictYamlBinding(problem -> this.problems.add(TagSetProblem.of(problem)));

	private TagSetBinder() {
	}

	static BindResult bind(MapNode root) {
		TagSetBinder binder = new TagSetBinder();
		TagSetDraft draft = binder.root(root);
		return new BindResult(draft, binder.problems);
	}

	/** 바인딩 결과 — 문제가 있으면 초안은 일부만 채워져 있다 */
	record BindResult(TagSetDraft draft, List<TagSetProblem> problems) {

		BindResult {
			problems = List.copyOf(problems);
		}

	}

	private TagSetDraft root(MapNode root) {
		YamlPath path = YamlPath.ROOT;
		this.binding.map(root, path);
		Map<String, YamlTree> entries = this.binding.entries(root, path, ROOT_KEYS::contains);
		return new TagSetDraft(root.location(), questions(entries.get(QUESTIONS), path.child(QUESTIONS)),
				thresholds(entries.get(THRESHOLDS), path.child(THRESHOLDS)),
				list(entries.get(TOPICS), path.child(TOPICS), this::topic),
				list(entries.get(REGIONS), path.child(REGIONS), this::tag),
				list(entries.get(FORMATS), path.child(FORMATS), this::format),
				list(entries.get(IMPORTANCE), path.child(IMPORTANCE), this::level),
				list(entries.get(LINEAGE), path.child(LINEAGE), this::lineage));
	}

	private @Nullable QuestionsDraft questions(@Nullable YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, QUESTION_KEYS::contains);
		return new QuestionsDraft(path, map.location(), string(entries, TOPIC, path), string(entries, REGION, path),
				string(entries, FORMAT, path), string(entries, IMPORTANCE, path));
	}

	private @Nullable ThresholdsDraft thresholds(@Nullable YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, THRESHOLD_KINDS::contains);
		return new ThresholdsDraft(path, map.location(), threshold(entries.get(TOPIC), path.child(TOPIC)),
				threshold(entries.get(REGION), path.child(REGION)));
	}

	private @Nullable ThresholdDraft threshold(@Nullable YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, THRESHOLD_KEYS::contains);
		return new ThresholdDraft(path, map.location(),
				this.binding.decimal(entries.get(TAG_THRESHOLD), path.child(TAG_THRESHOLD)),
				this.binding.decimal(entries.get(STORE_THRESHOLD), path.child(STORE_THRESHOLD)));
	}

	private @Nullable TopicDraft topic(YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, TOPIC_KEYS::contains);
		return new TopicDraft(path, map.location(), string(entries, KEY, path), string(entries, NAME, path),
				string(entries, DESCRIPTION, path), list(entries.get(SUBTOPICS), path.child(SUBTOPICS), this::tag));
	}

	private @Nullable TagDraft tag(YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, TAG_KEYS::contains);
		return new TagDraft(path, map.location(), string(entries, KEY, path), string(entries, NAME, path),
				string(entries, DESCRIPTION, path));
	}

	private @Nullable FormatDraft format(YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, FORMAT_KEYS::contains);
		return new FormatDraft(path, map.location(), string(entries, KEY, path), string(entries, NAME, path),
				string(entries, DESCRIPTION, path),
				this.binding.bool(entries.get(EXCLUDED_BY_DEFAULT), path.child(EXCLUDED_BY_DEFAULT)));
	}

	private @Nullable LevelDraft level(YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, LEVEL_KEYS::contains);
		return new LevelDraft(path, map.location(), this.binding.integer(entries.get(LEVEL), path.child(LEVEL)),
				string(entries, DESCRIPTION, path));
	}

	private @Nullable LineageDraft lineage(YamlTree node, YamlPath path) {
		MapNode map = this.binding.map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, LINEAGE_KEYS::contains);
		return new LineageDraft(path, map.location(), string(entries, KIND, path), string(entries, CHANGE, path),
				this.binding.strings(entries.get(FROM), path.child(FROM)),
				this.binding.strings(entries.get(TO), path.child(TO)));
	}

	private @Nullable Located<String> string(Map<String, YamlTree> entries, String key, YamlPath path) {
		return this.binding.string(entries.get(key), path.child(key));
	}

	/** 목록의 항목을 읽는다 — 쓸 수 없는 항목은 빼고 알린다 */
	private <T> @Nullable Located<List<T>> list(@Nullable YamlTree node, YamlPath path,
			ItemReader<T> reader) {
		ListNode list = this.binding.list(node, path);
		if (list == null) {
			return null;
		}
		List<T> items = new ArrayList<>();
		for (int i = 0; i < list.items().size(); i++) {
			T item = reader.read(list.items().get(i), path.index(i));
			if (item != null) {
				items.add(item);
			}
		}
		return new Located<>(items, path, list.location());
	}

	@FunctionalInterface
	private interface ItemReader<T> {

		@Nullable T read(YamlTree node, YamlPath path);

	}

}
