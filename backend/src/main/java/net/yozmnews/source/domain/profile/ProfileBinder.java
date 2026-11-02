package net.yozmnews.source.domain.profile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.platform.StrictYamlBinding;
import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
import net.yozmnews.platform.YamlTree;
import net.yozmnews.platform.YamlTree.ListNode;
import net.yozmnews.platform.YamlTree.MapNode;
import net.yozmnews.source.domain.profile.ProfileDraft.DatesDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.FeedDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.PreCorrectionDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.SourceDraft;
import org.jspecify.annotations.Nullable;

/**
 * 트리 → 초안 바인더 — docs/specs/source.md §4 · source-R32.
 * <p>
 * 공통 거부는 {@link StrictYamlBinding} 이 하고, 수집 프로필의 키 목록 · 피드 식별자 중복 · 알 수 없는 기사 항목은 여기서 판정한다. 문제는
 * 모두 한 목록에 찾은 순서대로 모은다. 아래 키 이름은 DB 에 저장된 운영자 프로필이 가리킨다 — 키 이름을 바꾸려면 저장된 프로필의 데이터 이행이 함께 있어야 한다.
 */
final class ProfileBinder {

	static final String SOURCE = "source";

	static final String NAME = "name";

	static final String HOMEPAGE = "homepage";

	static final String LANGUAGE = "language";

	static final String TERMS_REVIEWED_AT = "termsReviewedAt";

	static final String FEEDS = "feeds";

	static final String KEY = "key";

	static final String URL = "url";

	static final String SECTION = "section";

	static final String POLL_INTERVAL_MINUTES = "pollIntervalMinutes";

	static final String PRE_CORRECTIONS = "preCorrections";

	static final String RULE = "rule";

	static final String CHARSET = "charset";

	static final String ITEMS = "items";

	static final String NAMESPACES = "namespaces";

	static final String DATES = "dates";

	static final String FORMATS = "formats";

	static final String TIME_ZONE = "timeZone";

	private static final Set<String> ROOT_KEYS = Set.of(SOURCE, TERMS_REVIEWED_AT, FEEDS, POLL_INTERVAL_MINUTES,
			PRE_CORRECTIONS, ITEMS, NAMESPACES, DATES);

	private static final Set<String> SOURCE_KEYS = Set.of(NAME, HOMEPAGE, LANGUAGE);

	private static final Set<String> FEED_KEYS = Set.of(KEY, URL, SECTION, POLL_INTERVAL_MINUTES);

	private static final Set<String> PRE_CORRECTION_KEYS = Set.of(RULE, CHARSET);

	private static final Set<String> DATES_KEYS = Set.of(FORMATS, TIME_ZONE);

	private final List<ProfileProblem> problems = new ArrayList<>();

	private final StrictYamlBinding binding = new StrictYamlBinding(problem -> this.problems.add(ProfileProblem.of(problem)));

	private ProfileBinder() {
	}

	static BindResult bind(MapNode root) {
		ProfileBinder binder = new ProfileBinder();
		ProfileDraft draft = binder.root(root);
		return new BindResult(draft, binder.problems);
	}

	/** 바인딩 결과 — 문제가 있으면 초안은 일부만 채워져 있다 */
	record BindResult(ProfileDraft draft, List<ProfileProblem> problems) {

		BindResult {
			problems = List.copyOf(problems);
		}

	}

	private ProfileDraft root(MapNode root) {
		YamlPath path = YamlPath.ROOT;
		this.binding.map(root, path);
		Map<String, YamlTree> entries = entries(root, path, ROOT_KEYS);
		return new ProfileDraft(root.location(), source(entries.get(SOURCE), path.child(SOURCE)),
				string(entries.get(TERMS_REVIEWED_AT), path.child(TERMS_REVIEWED_AT)),
				feeds(entries.get(FEEDS), path.child(FEEDS)),
				integer(entries.get(POLL_INTERVAL_MINUTES), path.child(POLL_INTERVAL_MINUTES)),
				preCorrections(entries.get(PRE_CORRECTIONS), path.child(PRE_CORRECTIONS)),
				items(entries.get(ITEMS), path.child(ITEMS)), namespaces(entries.get(NAMESPACES), path.child(NAMESPACES)),
				dates(entries.get(DATES), path.child(DATES)));
	}

	private @Nullable SourceDraft source(@Nullable YamlTree node, YamlPath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = entries(map, path, SOURCE_KEYS);
		return new SourceDraft(path, map.location(), string(entries.get(NAME), path.child(NAME)),
				string(entries.get(HOMEPAGE), path.child(HOMEPAGE)), string(entries.get(LANGUAGE), path.child(LANGUAGE)));
	}

	private @Nullable Located<List<FeedDraft>> feeds(@Nullable YamlTree node, YamlPath path) {
		ListNode list = list(node, path);
		if (list == null) {
			return null;
		}
		List<FeedDraft> feeds = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (int i = 0; i < list.items().size(); i++) {
			YamlPath feedPath = path.index(i);
			MapNode map = map(list.items().get(i), feedPath);
			if (map == null) {
				continue;
			}
			Map<String, YamlTree> entries = entries(map, feedPath, FEED_KEYS);
			Located<String> key = string(entries.get(KEY), feedPath.child(KEY));
			if (key != null && !keys.add(key.value())) {
				problem(key.path(), ProblemCode.DUPLICATE_FEED_KEY, "피드 식별자 '" + key.value() + "' 가 앞의 피드와 겹친다",
						key.location());
			}
			feeds.add(new FeedDraft(feedPath, map.location(), key, string(entries.get(URL), feedPath.child(URL)),
					string(entries.get(SECTION), feedPath.child(SECTION)),
					integer(entries.get(POLL_INTERVAL_MINUTES), feedPath.child(POLL_INTERVAL_MINUTES))));
		}
		return new Located<>(feeds, path, list.location());
	}

	private @Nullable Located<List<PreCorrectionDraft>> preCorrections(@Nullable YamlTree node, YamlPath path) {
		ListNode list = list(node, path);
		if (list == null) {
			return null;
		}
		List<PreCorrectionDraft> corrections = new ArrayList<>();
		for (int i = 0; i < list.items().size(); i++) {
			YamlPath correctionPath = path.index(i);
			MapNode map = map(list.items().get(i), correctionPath);
			if (map == null) {
				continue;
			}
			Map<String, YamlTree> entries = entries(map, correctionPath, PRE_CORRECTION_KEYS);
			corrections.add(new PreCorrectionDraft(correctionPath, map.location(),
					string(entries.get(RULE), correctionPath.child(RULE)),
					string(entries.get(CHARSET), correctionPath.child(CHARSET))));
		}
		return new Located<>(corrections, path, list.location());
	}

	private @Nullable Located<Map<ItemField, Located<List<Located<String>>>>> items(@Nullable YamlTree node,
			YamlPath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, key -> ItemField.ofKey(key).isPresent(),
				(keyPath, key, location) -> problem(keyPath, ProblemCode.UNKNOWN_ITEM_FIELD, "알 수 없는 기사 항목 '" + key + "'",
						location));
		Map<ItemField, Located<List<Located<String>>>> items = new EnumMap<>(ItemField.class);
		entries.forEach((key, value) -> {
			ItemField field = ItemField.ofKey(key).orElseThrow();
			Located<List<Located<String>>> sources = strings(value, path.child(key));
			if (sources != null) {
				items.put(field, sources);
			}
		});
		return new Located<>(items, path, map.location());
	}

	private @Nullable Located<Map<String, Located<String>>> namespaces(@Nullable YamlTree node, YamlPath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = this.binding.entries(map, path, key -> true);
		Map<String, Located<String>> namespaces = new LinkedHashMap<>();
		entries.forEach((prefix, value) -> {
			Located<String> uri = string(value, path.child(prefix));
			if (uri != null) {
				namespaces.put(prefix, uri);
			}
		});
		return new Located<>(namespaces, path, map.location());
	}

	private @Nullable DatesDraft dates(@Nullable YamlTree node, YamlPath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, YamlTree> entries = entries(map, path, DATES_KEYS);
		return new DatesDraft(path, map.location(), strings(entries.get(FORMATS), path.child(FORMATS)),
				string(entries.get(TIME_ZONE), path.child(TIME_ZONE)));
	}

	private Map<String, YamlTree> entries(MapNode map, YamlPath path, Set<String> known) {
		return this.binding.entries(map, path, known::contains);
	}

	private @Nullable MapNode map(@Nullable YamlTree node, YamlPath path) {
		return this.binding.map(node, path);
	}

	private @Nullable ListNode list(@Nullable YamlTree node, YamlPath path) {
		return this.binding.list(node, path);
	}

	private @Nullable Located<String> string(@Nullable YamlTree node, YamlPath path) {
		return this.binding.string(node, path);
	}

	private @Nullable Located<List<Located<String>>> strings(@Nullable YamlTree node, YamlPath path) {
		return this.binding.strings(node, path);
	}

	private @Nullable Located<Integer> integer(@Nullable YamlTree node, YamlPath path) {
		return this.binding.integer(node, path);
	}

	private void problem(YamlPath path, ProblemCode code, String message, TextLocation location) {
		this.problems.add(new ProfileProblem(path, code, message, location));
	}

}
