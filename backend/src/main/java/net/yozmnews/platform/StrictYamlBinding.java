package net.yozmnews.platform;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.yozmnews.platform.YamlTree.AliasNode;
import net.yozmnews.platform.YamlTree.ListNode;
import net.yozmnews.platform.YamlTree.MapNode;
import net.yozmnews.platform.YamlTree.ScalarKind;
import net.yozmnews.platform.YamlTree.ScalarNode;
import org.jspecify.annotations.Nullable;

/**
 * YAML 트리를 엄격하게 읽는 도구 — docs/specs/platform.md §3 · platform-R17.
 * <p>
 * 공통 거부(알 수 없는 키 · 같은 매핑 안 중복 키 · 문자열이 아닌 키 · 별칭 · 앵커 · 명시 태그 · 빈 값 · 형 오류)를 판정하고, 문제는 찾은
 * 순서대로 생성자의 소비자에게 넘긴다 — 호출자가 자기 문제와 같은 목록에 넣으면 원문 순서가 그대로다. 쓸 수 없는 값은 null 로 돌려준다. 문서
 * 하나에 하나를 쓴다.
 */
public final class StrictYamlBinding {

	private final Consumer<YamlProblem> problems;

	/**
	 * 도구를 만든다.
	 * @param problems 공통 문제를 찾은 순서대로 받는다
	 */
	public StrictYamlBinding(Consumer<YamlProblem> problems) {
		this.problems = Objects.requireNonNull(problems, "problems");
	}

	/**
	 * 매핑의 항목을 키 → 값으로 모은다 — 키가 문자열 스칼라가 아니거나, 앞에 나온 키와 같거나, 알 수 없는 키면 문제로 알리고 뺀다.
	 * @param known 알려진 키인가 — 무엇이 알려진 키인지는 문서를 가진 모듈이 정한다
	 * @return 텍스트에 나온 순서의 항목
	 */
	public Map<String, YamlTree> entries(MapNode map, YamlPath path, Predicate<String> known) {
		return entries(map, path, known, (keyPath, key, location) -> problem(keyPath, YamlProblemKind.UNKNOWN_KEY,
				"알 수 없는 항목 '" + key + "'", location));
	}

	/**
	 * 매핑의 항목을 키 → 값으로 모은다 — 알 수 없는 키는 호출자가 자기 종류로 알린다. 나머지는
	 * {@link #entries(MapNode, YamlPath, Predicate)} 와 같다.
	 * @param onUnknown 알 수 없는 키를 만났을 때 — 다른 문제와 같은 순서 자리에서 부른다
	 * @return 텍스트에 나온 순서의 항목
	 */
	public Map<String, YamlTree> entries(MapNode map, YamlPath path, Predicate<String> known, UnknownKey onUnknown) {
		Objects.requireNonNull(onUnknown, "onUnknown");
		Map<String, YamlTree> entries = new LinkedHashMap<>();
		for (YamlTree.Entry entry : map.entries()) {
			YamlTree keyNode = entry.key();
			if (!usable(keyNode, path)) {
				continue;
			}
			if (!(keyNode instanceof ScalarNode scalar)) {
				problem(path, YamlProblemKind.WRONG_TYPE, "키는 문자열이어야 한다", keyNode.location());
				continue;
			}
			String key = scalar.text();
			YamlPath keyPath = path.child(key);
			if (entries.containsKey(key)) {
				problem(keyPath, YamlProblemKind.DUPLICATE_KEY, "같은 키 '" + key + "' 가 앞에 또 있다", scalar.location());
				continue;
			}
			if (!known.test(key)) {
				onUnknown.report(keyPath, key, scalar.location());
				continue;
			}
			entries.put(key, entry.value());
		}
		return entries;
	}

	/**
	 * 매핑을 읽는다.
	 * @return 없거나 쓸 수 없으면 null
	 */
	public @Nullable MapNode map(@Nullable YamlTree node, YamlPath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof MapNode map) {
			return map;
		}
		problem(path, YamlProblemKind.WRONG_TYPE, "항목을 담은 매핑이어야 한다", node.location());
		return null;
	}

	/**
	 * 목록을 읽는다.
	 * @return 없거나 쓸 수 없으면 null
	 */
	public @Nullable ListNode list(@Nullable YamlTree node, YamlPath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ListNode list) {
			return list;
		}
		problem(path, YamlProblemKind.WRONG_TYPE, "목록이어야 한다", node.location());
		return null;
	}

	/**
	 * 문자열 값을 읽는다 — 문자열 · 수 · 불리언 스칼라의 원문을 받는다.
	 * @return 없거나 쓸 수 없으면 null
	 */
	public @Nullable Located<String> string(@Nullable YamlTree node, YamlPath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ScalarNode scalar) {
			return new Located<>(scalar.text(), path, scalar.location());
		}
		problem(path, YamlProblemKind.WRONG_TYPE, "문자열이어야 한다", node.location());
		return null;
	}

	/**
	 * 문자열 목록을 읽는다 — 쓸 수 없는 항목은 빼고 알린다.
	 * @return 없거나 목록이 아니면 null
	 */
	public @Nullable Located<List<Located<String>>> strings(@Nullable YamlTree node, YamlPath path) {
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

	/**
	 * 32비트 정수 값을 읽는다 — 소수 · 문자열 · 범위 밖 정수는 형 오류다.
	 * @return 없거나 쓸 수 없으면 null
	 */
	public @Nullable Located<Integer> integer(@Nullable YamlTree node, YamlPath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ScalarNode scalar && scalar.kind() == ScalarKind.INTEGER) {
			try {
				return new Located<>(Integer.parseInt(scalar.text()), path, scalar.location());
			}
			catch (NumberFormatException ex) {
				problem(path, YamlProblemKind.WRONG_TYPE,
						"정수 범위(" + Integer.MIN_VALUE + " ~ " + Integer.MAX_VALUE + ")를 벗어났다", scalar.location());
				return null;
			}
		}
		problem(path, YamlProblemKind.WRONG_TYPE, "정수여야 한다", node.location());
		return null;
	}

	/**
	 * 소수 값을 읽는다 — 정수 · 소수 스칼라를 받는다. 문자열(따옴표 친 수 포함) · 불리언과, 소수 스칼라라도 수로 옮길 수 없는 값
	 * ({@code .inf} · {@code .nan} · 범위 밖 지수)은 형 오류다.
	 * @return 없거나 쓸 수 없으면 null
	 */
	public @Nullable Located<BigDecimal> decimal(@Nullable YamlTree node, YamlPath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ScalarNode scalar
				&& (scalar.kind() == ScalarKind.INTEGER || scalar.kind() == ScalarKind.DECIMAL)) {
			try {
				return new Located<>(new BigDecimal(scalar.text()), path, scalar.location());
			}
			catch (NumberFormatException ex) {
				problem(path, YamlProblemKind.WRONG_TYPE, "수로 읽을 수 없다 — .inf · .nan · 범위 밖 지수는 쓸 수 없다",
						scalar.location());
				return null;
			}
		}
		problem(path, YamlProblemKind.WRONG_TYPE, "수여야 한다", node.location());
		return null;
	}

	/**
	 * 불리언 값을 읽는다 — 소문자 {@code true} · {@code false} 스칼라만 받는다.
	 * @return 없거나 쓸 수 없으면 null
	 */
	public @Nullable Located<Boolean> bool(@Nullable YamlTree node, YamlPath path) {
		if (node == null || !usable(node, path) || isNull(node, path)) {
			return null;
		}
		if (node instanceof ScalarNode scalar && scalar.kind() == ScalarKind.BOOLEAN) {
			return new Located<>(Boolean.parseBoolean(scalar.text()), path, scalar.location());
		}
		problem(path, YamlProblemKind.WRONG_TYPE, "true 또는 false 여야 한다", node.location());
		return null;
	}

	private boolean isNull(YamlTree node, YamlPath path) {
		if (node instanceof ScalarNode scalar && scalar.kind() == ScalarKind.NULL) {
			problem(path, YamlProblemKind.NULL_NOT_ALLOWED, "값이 비어 있다 — 쓰지 않을 항목이면 지운다", scalar.location());
			return true;
		}
		return false;
	}

	/**
	 * 별칭 · 앵커 · 명시 태그를 문제로 알린다. 별칭은 값이 없으므로 쓸 수 없다(false). 앵커 · 태그가 붙은 노드는 알린 뒤에도 값을 계속
	 * 읽어 다른 문제를 함께 모은다.
	 */
	private boolean usable(YamlTree node, YamlPath path) {
		if (node instanceof AliasNode alias) {
			problem(path, YamlProblemKind.ALIAS_NOT_ALLOWED, "YAML 별칭(*" + alias.anchorName() + ")은 쓸 수 없다 — 값을 직접 적는다",
					alias.location());
			return false;
		}
		if (node.anchor() != null) {
			problem(path, YamlProblemKind.ANCHOR_NOT_ALLOWED, "YAML 앵커(&" + node.anchor() + ")는 쓸 수 없다", node.location());
		}
		if (node.tag() != null) {
			problem(path, YamlProblemKind.TAG_NOT_ALLOWED, "YAML 태그(" + node.tag() + ")는 쓸 수 없다", node.location());
		}
		return true;
	}

	private void problem(YamlPath path, YamlProblemKind kind, String message, TextLocation location) {
		this.problems.accept(new YamlProblem(path, kind, message, location));
	}

	/** 알 수 없는 키를 호출자가 자기 종류로 알린다 */
	@FunctionalInterface
	public interface UnknownKey {

		void report(YamlPath keyPath, String key, TextLocation location);

	}

	/** 읽은 값과 그 경로 · 위치 */
	public record Located<T>(T value, YamlPath path, TextLocation location) {

		public Located {
			Objects.requireNonNull(value, "value");
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(location, "location");
		}

	}

}
