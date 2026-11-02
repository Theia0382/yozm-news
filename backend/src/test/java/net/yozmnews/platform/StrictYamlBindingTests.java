package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.YamlTree.MapNode;
import org.junit.jupiter.api.Test;

/**
 * YAML 트리의 엄격한 읽기 — 공통 거부의 종류 · 경로 · 위치 · 문구와 넘기는 순서를 본다.
 * <p>
 * 지키는 규칙: platform-R17
 */
class StrictYamlBindingTests {

	private final List<YamlProblem> problems = new ArrayList<>();

	private final StrictYamlBinding binding = new StrictYamlBinding(this.problems::add);

	@Test
	void collectsKnownEntriesInOrder() {
		MapNode root = read("""
				b: 1
				a: 2
				""");

		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, Set.of("a", "b")::contains);

		assertThat(entries.keySet()).containsExactly("b", "a");
		assertThat(this.problems).isEmpty();
	}

	@Test
	void reportsADuplicateKeyAndKeepsTheFirstValue() {
		MapNode root = read("""
				a: first
				a: second
				""");

		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, Set.of("a")::contains);

		assertThat(this.binding.string(entries.get("a"), YamlPath.of("a")).value()).isEqualTo("first");
		assertThat(this.problems).containsExactly(new YamlProblem(YamlPath.of("a"), YamlProblemKind.DUPLICATE_KEY,
				"같은 키 'a' 가 앞에 또 있다", new TextLocation(2, 1)));
	}

	@Test
	void reportsAnUnknownKey() {
		MapNode root = read("""
				a: 1
				zzz: 2
				""");

		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, Set.of("a")::contains);

		assertThat(entries.keySet()).containsExactly("a");
		assertThat(this.problems).containsExactly(new YamlProblem(YamlPath.of("zzz"), YamlProblemKind.UNKNOWN_KEY,
				"알 수 없는 항목 'zzz'", new TextLocation(2, 1)));
	}

	@Test
	void letsTheCallerReportAnUnknownKeyInItsOwnWords() {
		MapNode root = read("""
				zzz: 1
				""");
		List<String> reported = new ArrayList<>();

		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, key -> false,
				(keyPath, key, location) -> reported.add(keyPath + " " + key + " " + location));

		assertThat(entries).isEmpty();
		assertThat(reported).containsExactly("zzz zzz 1:1");
		assertThat(this.problems).isEmpty();
	}

	@Test
	void reportsAliasesAnchorsAndTags() {
		MapNode root = read("""
				a: &x 1
				b: *x
				c: !!str 2
				""");

		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, Set.of("a", "b", "c")::contains);
		Located<String> a = this.binding.string(entries.get("a"), YamlPath.of("a"));
		Located<String> b = this.binding.string(entries.get("b"), YamlPath.of("b"));
		Located<String> c = this.binding.string(entries.get("c"), YamlPath.of("c"));

		assertThat(a.value()).isEqualTo("1");
		assertThat(b).isNull();
		assertThat(c.value()).isEqualTo("2");
		assertThat(this.problems).containsExactly(
				new YamlProblem(YamlPath.of("a"), YamlProblemKind.ANCHOR_NOT_ALLOWED, "YAML 앵커(&x)는 쓸 수 없다",
						new TextLocation(1, 4)),
				new YamlProblem(YamlPath.of("b"), YamlProblemKind.ALIAS_NOT_ALLOWED,
						"YAML 별칭(*x)은 쓸 수 없다 — 값을 직접 적는다", new TextLocation(2, 4)),
				new YamlProblem(YamlPath.of("c"), YamlProblemKind.TAG_NOT_ALLOWED,
						"YAML 태그(tag:yaml.org,2002:str)는 쓸 수 없다", new TextLocation(3, 4)));
	}

	@Test
	void reportsAnEmptyValue() {
		MapNode root = read("""
				a:
				""");

		YamlTree value = this.binding.entries(root, YamlPath.ROOT, Set.of("a")::contains).get("a");

		assertThat(this.binding.string(value, YamlPath.of("a"))).isNull();
		assertThat(this.problems).containsExactly(new YamlProblem(YamlPath.of("a"), YamlProblemKind.NULL_NOT_ALLOWED,
				"값이 비어 있다 — 쓰지 않을 항목이면 지운다", new TextLocation(1, 3)));
	}

	@Test
	void reportsWrongTypes() {
		MapNode root = read("""
				m: text
				l: text
				s: [1]
				i: 1.5
				""");
		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, Set.of("m", "l", "s", "i")::contains);

		assertThat(this.binding.map(entries.get("m"), YamlPath.of("m"))).isNull();
		assertThat(this.binding.list(entries.get("l"), YamlPath.of("l"))).isNull();
		assertThat(this.binding.string(entries.get("s"), YamlPath.of("s"))).isNull();
		assertThat(this.binding.integer(entries.get("i"), YamlPath.of("i"))).isNull();
		assertThat(this.problems).extracting(YamlProblem::kind, YamlProblem::message)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(YamlProblemKind.WRONG_TYPE, "항목을 담은 매핑이어야 한다"),
					org.assertj.core.groups.Tuple.tuple(YamlProblemKind.WRONG_TYPE, "목록이어야 한다"),
					org.assertj.core.groups.Tuple.tuple(YamlProblemKind.WRONG_TYPE, "문자열이어야 한다"),
					org.assertj.core.groups.Tuple.tuple(YamlProblemKind.WRONG_TYPE, "정수여야 한다"));
	}

	@Test
	void readsDecimalsFromIntegerAndDecimalScalars() {
		MapNode root = read("""
				half: 0.5
				one: 1
				quoted: "0.5"
				flag: true
				""");
		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT,
				Set.of("half", "one", "quoted", "flag")::contains);

		assertThat(this.binding.decimal(entries.get("half"), YamlPath.of("half")).value()).isEqualTo(new BigDecimal("0.5"));
		assertThat(this.binding.decimal(entries.get("one"), YamlPath.of("one")).value()).isEqualTo(new BigDecimal("1"));
		assertThat(this.binding.decimal(entries.get("quoted"), YamlPath.of("quoted"))).isNull();
		assertThat(this.binding.decimal(entries.get("flag"), YamlPath.of("flag"))).isNull();
		assertThat(this.problems).containsExactly(
				new YamlProblem(YamlPath.of("quoted"), YamlProblemKind.WRONG_TYPE, "수여야 한다", new TextLocation(3, 9)),
				new YamlProblem(YamlPath.of("flag"), YamlProblemKind.WRONG_TYPE, "수여야 한다", new TextLocation(4, 7)));
	}

	@Test
	void reportsDecimalScalarsThatAreNotNumbersAsWrongTypes() {
		MapNode root = read("""
				inf: .inf
				minus: -.inf
				nan: .nan
				huge: 1e99999999999
				exp: 1e-3
				""");
		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT,
				Set.of("inf", "minus", "nan", "huge", "exp")::contains);

		assertThat(this.binding.decimal(entries.get("inf"), YamlPath.of("inf"))).isNull();
		assertThat(this.binding.decimal(entries.get("minus"), YamlPath.of("minus"))).isNull();
		assertThat(this.binding.decimal(entries.get("nan"), YamlPath.of("nan"))).isNull();
		assertThat(this.binding.decimal(entries.get("huge"), YamlPath.of("huge"))).isNull();
		assertThat(this.binding.decimal(entries.get("exp"), YamlPath.of("exp")).value()).isEqualByComparingTo("0.001");
		String message = "수로 읽을 수 없다 — .inf · .nan · 범위 밖 지수는 쓸 수 없다";
		assertThat(this.problems).containsExactly(
				new YamlProblem(YamlPath.of("inf"), YamlProblemKind.WRONG_TYPE, message, new TextLocation(1, 6)),
				new YamlProblem(YamlPath.of("minus"), YamlProblemKind.WRONG_TYPE, message, new TextLocation(2, 8)),
				new YamlProblem(YamlPath.of("nan"), YamlProblemKind.WRONG_TYPE, message, new TextLocation(3, 6)),
				new YamlProblem(YamlPath.of("huge"), YamlProblemKind.WRONG_TYPE, message, new TextLocation(4, 7)));
	}

	@Test
	void readsBooleansOnlyFromBooleanScalars() {
		MapNode root = read("""
				yes: true
				no: false
				word: no
				""");
		Map<String, YamlTree> entries = this.binding.entries(root, YamlPath.ROOT, Set.of("yes", "no", "word")::contains);

		assertThat(this.binding.bool(entries.get("yes"), YamlPath.of("yes")).value()).isTrue();
		assertThat(this.binding.bool(entries.get("no"), YamlPath.of("no")).value()).isFalse();
		assertThat(this.binding.bool(entries.get("word"), YamlPath.of("word"))).isNull();
		assertThat(this.problems).containsExactly(new YamlProblem(YamlPath.of("word"), YamlProblemKind.WRONG_TYPE,
				"true 또는 false 여야 한다", new TextLocation(3, 7)));
	}

	@Test
	void handsProblemsOverInTheOrderTheyWereFound() {
		MapNode root = read("""
				zzz: 1
				a: 1
				a: 2
				""");

		this.binding.entries(root, YamlPath.ROOT, Set.of("a")::contains);

		assertThat(this.problems).extracting(YamlProblem::kind)
			.containsExactly(YamlProblemKind.UNKNOWN_KEY, YamlProblemKind.DUPLICATE_KEY);
	}

	private static MapNode read(String text) {
		YamlRead read = new YamlTextReader(65_536).read(text);
		assertThat(read).isInstanceOf(YamlRead.Read.class);
		return ((YamlRead.Read) read).root();
	}

}
