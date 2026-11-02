package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.yozmnews.platform.YamlTree.AliasNode;
import net.yozmnews.platform.YamlTree.Entry;
import net.yozmnews.platform.YamlTree.ListNode;
import net.yozmnews.platform.YamlTree.MapNode;
import net.yozmnews.platform.YamlTree.ScalarKind;
import net.yozmnews.platform.YamlTree.ScalarNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * YAML 원문 읽기 — 텍스트를 직접 넣어 트리와 문제를 본다. 상한은 수집 프로필과 같은 65,536자로 둔다.
 * <p>
 * 지키는 규칙: platform-R17 · source-R32
 */
class YamlTextReaderTests {

	private final YamlTextReader reader = new YamlTextReader(65_536);

	@Test
	void keepsMappingsListsAndScalarsInOrder() {
		MapNode root = read("""
				source:
				  name: 블로터
				feeds:
				  - key: all
				  - key: breaking
				""");

		assertThat(keys(root)).containsExactly("source", "feeds");
		MapNode source = (MapNode) root.entries().get(0).value();
		assertThat(source.entries().get(0).value()).isEqualTo(
				new ScalarNode(ScalarKind.STRING, "블로터", new TextLocation(2, 9), null, null));
		ListNode feeds = (ListNode) root.entries().get(1).value();
		assertThat(feeds.items()).hasSize(2);
		assertThat(feeds.location()).isEqualTo(new TextLocation(4, 3));
		assertThat(scalar(((MapNode) feeds.items().get(1)).entries().get(0).value()).text()).isEqualTo("breaking");
	}

	@Test
	void keepsBothEntriesOfADuplicateKey() {
		MapNode root = read("""
				timeZone: Asia/Seoul
				timeZone: UTC
				""");

		assertThat(keys(root)).containsExactly("timeZone", "timeZone");
		assertThat(scalar(root.entries().get(0).value()).text()).isEqualTo("Asia/Seoul");
		assertThat(scalar(root.entries().get(1).value()).text()).isEqualTo("UTC");
		assertThat(root.entries().get(1).key().location()).isEqualTo(new TextLocation(2, 1));
	}

	@Test
	void keepsAnAliasAsItsAnchorNameWithoutExpanding() {
		MapNode root = read("""
				a: &tz Asia/Seoul
				b: *tz
				c: *undefined
				""");

		ScalarNode anchored = scalar(root.entries().get(0).value());
		assertThat(anchored.anchor()).isEqualTo("tz");
		assertThat(anchored.text()).isEqualTo("Asia/Seoul");
		assertThat(root.entries().get(1).value()).isEqualTo(new AliasNode("tz", new TextLocation(2, 4)));
		assertThat(root.entries().get(2).value()).isEqualTo(new AliasNode("undefined", new TextLocation(3, 4)));
	}

	@Test
	void recordsTagsAndAnchorsOnTheNodeTheyBelongTo() {
		MapNode tagged = read("""
				!!str source: x
				name: y
				""");
		assertThat(tagged.entries().get(0).key().tag()).isEqualTo("tag:yaml.org,2002:str");
		assertThat(tagged.entries().get(0).value().tag()).isNull();
		assertThat(tagged.entries().get(1).key().tag()).isNull();

		MapNode anchoredMap = read("""
				src: &m
				  name: x
				""");
		MapNode inner = (MapNode) anchoredMap.entries().get(0).value();
		assertThat(inner.anchor()).isEqualTo("m");
		assertThat(inner.entries().get(0).key().anchor()).isNull();

		MapNode valueTag = read("x: !!java.lang.Runtime y\n");
		assertThat(valueTag.entries().get(0).value().tag()).isEqualTo("tag:yaml.org,2002:java.lang.Runtime");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			015        | STRING  | 015
			no         | STRING  | no
			True       | STRING  | True
			~          | STRING  | ~
			'"1"'      | STRING  | 1
			1          | INTEGER | 1
			-15        | INTEGER | -15
			15.0       | DECIMAL | 15.0
			true       | BOOLEAN | true
			null       | NULL    | null
			$standard  | STRING  | $standard
			""")
	void resolvesScalarKindsWithTheJsonSchema(String yaml, ScalarKind kind, String text) {
		ScalarNode value = scalar(read("v: " + yaml + "\n").entries().get(0).value());

		assertThat(value.kind()).isEqualTo(kind);
		assertThat(value.text()).isEqualTo(text);
	}

	@Test
	void readsAnEmptyValueAsNull() {
		ScalarNode value = scalar(read("timeZone:\n").entries().get(0).value());

		assertThat(value.kind()).isEqualTo(ScalarKind.NULL);
		assertThat(value.text()).isEmpty();
	}

	@Test
	void readsAComplexKeyAsANonScalarNode() {
		Entry entry = read("? [a, b]\n: c\n").entries().get(0);

		assertThat(entry.key()).isInstanceOf(ListNode.class);
		assertThat(scalar(entry.value()).text()).isEqualTo("c");
	}

	@Test
	void readsJsonText() {
		MapNode root = read("{\"pollIntervalMinutes\": 30, \"items\": {\"summary\": [\"$standard\"]}, \"x\": [true, null]}");

		assertThat(keys(root)).containsExactly("pollIntervalMinutes", "items", "x");
		assertThat(scalar(root.entries().get(0).value()).kind()).isEqualTo(ScalarKind.INTEGER);
		ListNode x = (ListNode) root.entries().get(2).value();
		assertThat(x.items()).extracting(node -> scalar(node).kind())
			.containsExactly(ScalarKind.BOOLEAN, ScalarKind.NULL);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			''
			'# 주석만 있다'
			'---'
			""")
	void readsTextWithoutContentAsAnEmptyMapping(String text) {
		assertThat(read(text).entries()).isEmpty();
	}

	@Test
	void readsDeeplyNestedTextWithoutOverflowingTheStack() {
		String nested = "a: " + "[".repeat(20_000) + "]".repeat(20_000);

		assertThat(read(nested).entries()).hasSize(1);
	}

	@Test
	void rejectsATopLevelThatIsNotAMapping() {
		assertThat(unreadable("- a\n- b\n")).isEqualTo(problem(YamlProblemKind.NOT_A_MAPPING, new TextLocation(1, 1)));
		assertThat(unreadable("just text\n").kind()).isEqualTo(YamlProblemKind.NOT_A_MAPPING);
	}

	@Test
	void rejectsMoreThanOneDocument() {
		YamlProblem problem = unreadable("a: 1\n---\nb: 2\n");

		assertThat(problem.kind()).isEqualTo(YamlProblemKind.MULTIPLE_DOCUMENTS);
		assertThat(problem.location()).isEqualTo(new TextLocation(2, 1));
	}

	@Test
	void reportsSyntaxErrorsWithTheirLocation() {
		YamlProblem tab = unreadable("a:\n\tb: 1\n");
		assertThat(tab.kind()).isEqualTo(YamlProblemKind.SYNTAX_ERROR);
		assertThat(tab.location()).isEqualTo(new TextLocation(2, 1));

		YamlProblem flow = unreadable("a: [1, 2\n");
		assertThat(flow.kind()).isEqualTo(YamlProblemKind.SYNTAX_ERROR);
		assertThat(flow.location()).isEqualTo(new TextLocation(2, 1));
	}

	@Test
	void readsTextUpToTheLengthLimit() {
		String atLimit = "a: " + "x".repeat(65_536 - 3);

		assertThat(atLimit).hasSize(65_536);
		assertThat(read(atLimit).entries()).hasSize(1);
	}

	@Test
	void rejectsTextOverTheLengthLimit() {
		String overLimit = "a: " + "x".repeat(65_537 - 3);

		assertThat(overLimit).hasSize(65_537);
		assertThat(unreadable(overLimit)).isEqualTo(new YamlProblem(YamlPath.ROOT, YamlProblemKind.TOO_LARGE,
				"원문은 65536자까지다 — 지금 65537자", TextLocation.UNKNOWN));
	}

	@Test
	void takesTheLengthLimitFromTheCaller() {
		YamlTextReader small = new YamlTextReader(10);

		assertThat(small.read("a: 1234567")).isInstanceOf(YamlRead.Read.class);
		assertThat(small.read("a: 12345678")).isEqualTo(new YamlRead.Unreadable(new YamlProblem(YamlPath.ROOT,
				YamlProblemKind.TOO_LARGE, "원문은 10자까지다 — 지금 11자", TextLocation.UNKNOWN)));
		assertThatThrownBy(() -> new YamlTextReader(0)).isInstanceOf(IllegalArgumentException.class);
	}

	private MapNode read(String text) {
		YamlRead result = this.reader.read(text);
		assertThat(result).isInstanceOf(YamlRead.Read.class);
		return ((YamlRead.Read) result).root();
	}

	private YamlProblem unreadable(String text) {
		YamlRead result = this.reader.read(text);
		assertThat(result).isInstanceOf(YamlRead.Unreadable.class);
		return ((YamlRead.Unreadable) result).problem();
	}

	private static YamlProblem problem(YamlProblemKind kind, TextLocation location) {
		return new YamlProblem(YamlPath.ROOT, kind,
				"최상위는 항목을 담은 매핑이어야 한다", location);
	}

	private static List<String> keys(MapNode map) {
		return map.entries().stream().map(entry -> scalar(entry.key()).text()).toList();
	}

	private static ScalarNode scalar(YamlTree node) {
		assertThat(node).isInstanceOf(ScalarNode.class);
		return (ScalarNode) node;
	}

}
