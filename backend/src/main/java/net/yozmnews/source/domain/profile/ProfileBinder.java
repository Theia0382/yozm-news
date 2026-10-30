package net.yozmnews.source.domain.profile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.yozmnews.source.domain.profile.ProfileDraft.DatesDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.FeedDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.Located;
import net.yozmnews.source.domain.profile.ProfileDraft.PreCorrectionDraft;
import net.yozmnews.source.domain.profile.ProfileDraft.SourceDraft;
import net.yozmnews.source.domain.profile.text.ProfileTree;
import net.yozmnews.source.domain.profile.text.ProfileTree.AliasNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.ListNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.MapNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.ScalarKind;
import net.yozmnews.source.domain.profile.text.ProfileTree.ScalarNode;
import net.yozmnews.source.domain.profile.text.TextLocation;
import org.jspecify.annotations.Nullable;

/**
 * 트리 → 초안 바인더 — docs/specs/source.md §4 · source-R32.
 * <p>
 * 문제는 모두 모은다. 아래 키 이름은 DB 에 저장된 운영자 프로필이 가리킨다 — 키 이름을 바꾸려면 저장된 프로필의 데이터 이행이 함께 있어야 한다.
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
		ProfilePath path = ProfilePath.ROOT;
		usable(root, path);
		Map<String, ProfileTree> entries = entries(root, path, ROOT_KEYS::contains, ProblemCode.UNKNOWN_KEY);
		return new ProfileDraft(root.location(), source(entries.get(SOURCE), path.child(SOURCE)),
				string(entries.get(TERMS_REVIEWED_AT), path.child(TERMS_REVIEWED_AT)),
				feeds(entries.get(FEEDS), path.child(FEEDS)),
				integer(entries.get(POLL_INTERVAL_MINUTES), path.child(POLL_INTERVAL_MINUTES)),
				preCorrections(entries.get(PRE_CORRECTIONS), path.child(PRE_CORRECTIONS)),
				items(entries.get(ITEMS), path.child(ITEMS)), namespaces(entries.get(NAMESPACES), path.child(NAMESPACES)),
				dates(entries.get(DATES), path.child(DATES)));
	}

	private @Nullable SourceDraft source(@Nullable ProfileTree node, ProfilePath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, ProfileTree> entries = entries(map, path, SOURCE_KEYS::contains, ProblemCode.UNKNOWN_KEY);
		return new SourceDraft(path, map.location(), string(entries.get(NAME), path.child(NAME)),
				string(entries.get(HOMEPAGE), path.child(HOMEPAGE)), string(entries.get(LANGUAGE), path.child(LANGUAGE)));
	}

	private @Nullable Located<List<FeedDraft>> feeds(@Nullable ProfileTree node, ProfilePath path) {
		ListNode list = list(node, path);
		if (list == null) {
			return null;
		}
		List<FeedDraft> feeds = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (int i = 0; i < list.items().size(); i++) {
			ProfilePath feedPath = path.index(i);
			MapNode map = map(list.items().get(i), feedPath);
			if (map == null) {
				continue;
			}
			Map<String, ProfileTree> entries = entries(map, feedPath, FEED_KEYS::contains, ProblemCode.UNKNOWN_KEY);
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

	private @Nullable Located<List<PreCorrectionDraft>> preCorrections(@Nullable ProfileTree node, ProfilePath path) {
		ListNode list = list(node, path);
		if (list == null) {
			return null;
		}
		List<PreCorrectionDraft> corrections = new ArrayList<>();
		for (int i = 0; i < list.items().size(); i++) {
			ProfilePath correctionPath = path.index(i);
			MapNode map = map(list.items().get(i), correctionPath);
			if (map == null) {
				continue;
			}
			Map<String, ProfileTree> entries = entries(map, correctionPath, PRE_CORRECTION_KEYS::contains,
					ProblemCode.UNKNOWN_KEY);
			corrections.add(new PreCorrectionDraft(correctionPath, map.location(),
					string(entries.get(RULE), correctionPath.child(RULE)),
					string(entries.get(CHARSET), correctionPath.child(CHARSET))));
		}
		return new Located<>(corrections, path, list.location());
	}

	private @Nullable Located<Map<ItemField, Located<List<Located<String>>>>> items(@Nullable ProfileTree node,
			ProfilePath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, ProfileTree> entries = entries(map, path, key -> ItemField.ofKey(key).isPresent(),
				ProblemCode.UNKNOWN_ITEM_FIELD);
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

	private @Nullable Located<Map<String, Located<String>>> namespaces(@Nullable ProfileTree node, ProfilePath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, ProfileTree> entries = entries(map, path, key -> true, ProblemCode.UNKNOWN_KEY);
		Map<String, Located<String>> namespaces = new LinkedHashMap<>();
		entries.forEach((prefix, value) -> {
			Located<String> uri = string(value, path.child(prefix));
			if (uri != null) {
				namespaces.put(prefix, uri);
			}
		});
		return new Located<>(namespaces, path, map.location());
	}

	private @Nullable DatesDraft dates(@Nullable ProfileTree node, ProfilePath path) {
		MapNode map = map(node, path);
		if (map == null) {
			return null;
		}
		Map<String, ProfileTree> entries = entries(map, path, DATES_KEYS::contains, ProblemCode.UNKNOWN_KEY);
		return new DatesDraft(path, map.location(), strings(entries.get(FORMATS), path.child(FORMATS)),
				string(entries.get(TIME_ZONE), path.child(TIME_ZONE)));
	}

	/**
	 * 매핑의 항목을 키 → 값으로 모은다 — 키가 문자열 스칼라가 아니거나, 앞에 나온 키와 같거나, 알 수 없는 키면 문제로 알리고 뺀다
	 */
	private Map<String, ProfileTree> entries(MapNode map, ProfilePath path, Predicate<String> known,
			ProblemCode unknownCode) {
		Map<String, ProfileTree> entries = new LinkedHashMap<>();
		for (ProfileTree.Entry entry : map.entries()) {
			ProfileTree keyNode = entry.key();
			if (!usable(keyNode, path)) {
				continue;
			}
			if (!(keyNode instanceof ScalarNode scalar)) {
				problem(path, ProblemCode.WRONG_TYPE, "키는 문자열이어야 한다", keyNode.location());
				continue;
			}
			String key = scalar.text();
			ProfilePath keyPath = path.child(key);
			if (entries.containsKey(key)) {
				problem(keyPath, ProblemCode.DUPLICATE_KEY, "같은 키 '" + key + "' 가 앞에 또 있다", scalar.location());
				continue;
			}
			if (!known.test(key)) {
				problem(keyPath, unknownCode,
						(unknownCode == ProblemCode.UNKNOWN_ITEM_FIELD) ? "알 수 없는 기사 항목 '" + key + "'"
								: "알 수 없는 항목 '" + key + "'",
						scalar.location());
				continue;
			}
			entries.put(key, entry.value());
		}
		return entries;
	}

	private @Nullable MapNode map(@Nullable ProfileTree node, ProfilePath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof MapNode map) {
			return map;
		}
		problem(path, ProblemCode.WRONG_TYPE, "항목을 담은 매핑이어야 한다", node.location());
		return null;
	}

	private @Nullable ListNode list(@Nullable ProfileTree node, ProfilePath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ListNode list) {
			return list;
		}
		problem(path, ProblemCode.WRONG_TYPE, "목록이어야 한다", node.location());
		return null;
	}

	/** 문자열 값을 읽는다 — 문자열 · 수 · 불리언 스칼라의 원문을 받는다 */
	private @Nullable Located<String> string(@Nullable ProfileTree node, ProfilePath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ScalarNode scalar) {
			return new Located<>(scalar.text(), path, scalar.location());
		}
		problem(path, ProblemCode.WRONG_TYPE, "문자열이어야 한다", node.location());
		return null;
	}

	private @Nullable Located<List<Located<String>>> strings(@Nullable ProfileTree node, ProfilePath path) {
		ListNode list = list(node, path);
		if (list == null) {
			return null;
		}
		List<Located<String>> values = new ArrayList<>();
		for (int i = 0; i < list.items().size(); i++) {
			Located<String> value = string(list.items().get(i), path.index(i));
			if (value != null) {
				values.add(value);
			}
		}
		return new Located<>(values, path, list.location());
	}

	/** 32비트 정수 값을 읽는다 — 소수 · 문자열 · 범위 밖 정수는 형 오류다 */
	private @Nullable Located<Integer> integer(@Nullable ProfileTree node, ProfilePath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ScalarNode scalar && scalar.kind() == ScalarKind.INTEGER) {
			try {
				return new Located<>(Integer.parseInt(scalar.text()), path, scalar.location());
			}
			catch (NumberFormatException ex) {
				problem(path, ProblemCode.WRONG_TYPE,
						"정수 범위(" + Integer.MIN_VALUE + " ~ " + Integer.MAX_VALUE + ")를 벗어났다", scalar.location());
				return null;
			}
		}
		problem(path, ProblemCode.WRONG_TYPE, "정수여야 한다", node.location());
		return null;
	}

	private boolean isNull(ProfileTree node, ProfilePath path) {
		if (node instanceof ScalarNode scalar && scalar.kind() == ScalarKind.NULL) {
			problem(path, ProblemCode.NULL_NOT_ALLOWED, "값이 비어 있다 — 쓰지 않을 항목이면 지운다", scalar.location());
			return true;
		}
		return false;
	}

	/**
	 * 별칭 · 앵커 · 명시 태그를 문제로 알린다. 별칭은 값이 없으므로 쓸 수 없다(false). 앵커 · 태그가 붙은 노드는 알린 뒤에도 값을 계속
	 * 읽어 다른 문제를 함께 모은다.
	 */
	private boolean usable(ProfileTree node, ProfilePath path) {
		if (node instanceof AliasNode alias) {
			problem(path, ProblemCode.ALIAS_NOT_ALLOWED, "YAML 별칭(*" + alias.anchorName() + ")은 쓸 수 없다 — 값을 직접 적는다",
					alias.location());
			return false;
		}
		if (node.anchor() != null) {
			problem(path, ProblemCode.ANCHOR_NOT_ALLOWED, "YAML 앵커(&" + node.anchor() + ")는 쓸 수 없다", node.location());
		}
		if (node.tag() != null) {
			problem(path, ProblemCode.TAG_NOT_ALLOWED, "YAML 태그(" + node.tag() + ")는 쓸 수 없다", node.location());
		}
		return true;
	}

	private void problem(ProfilePath path, ProblemCode code, String message, TextLocation location) {
		this.problems.add(new ProfileProblem(path, code, message, location));
	}

}
