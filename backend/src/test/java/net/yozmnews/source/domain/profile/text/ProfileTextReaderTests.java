package net.yozmnews.source.domain.profile.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.source.domain.profile.ProblemCode;
import net.yozmnews.source.domain.profile.ProfileProblem;
import net.yozmnews.source.domain.profile.text.ProfileTree.AliasNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.Entry;
import net.yozmnews.source.domain.profile.text.ProfileTree.ListNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.MapNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.ScalarKind;
import net.yozmnews.source.domain.profile.text.ProfileTree.ScalarNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfileTextReaderTests {

	private final ProfileTextReader reader = new ProfileTextReader();

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
		assertThat(unreadable("- a\n- b\n")).isEqualTo(problem(ProblemCode.NOT_A_MAPPING, new TextLocation(1, 1)));
		assertThat(unreadable("just text\n").code()).isEqualTo(ProblemCode.NOT_A_MAPPING);
	}

	@Test
	void rejectsMoreThanOneDocument() {
		ProfileProblem problem = unreadable("a: 1\n---\nb: 2\n");

		assertThat(problem.code()).isEqualTo(ProblemCode.MULTIPLE_DOCUMENTS);
		assertThat(problem.location()).isEqualTo(new TextLocation(2, 1));
	}

	@Test
	void reportsSyntaxErrorsWithTheirLocation() {
		ProfileProblem tab = unreadable("a:\n\tb: 1\n");
		assertThat(tab.code()).isEqualTo(ProblemCode.SYNTAX_ERROR);
		assertThat(tab.location()).isEqualTo(new TextLocation(2, 1));

		ProfileProblem flow = unreadable("a: [1, 2\n");
		assertThat(flow.code()).isEqualTo(ProblemCode.SYNTAX_ERROR);
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
		assertThat(unreadable(overLimit).code()).isEqualTo(ProblemCode.TOO_LARGE);
	}

	private MapNode read(String text) {
		ReadResult result = this.reader.read(text);
		assertThat(result).isInstanceOf(ReadResult.Read.class);
		return ((ReadResult.Read) result).root();
	}

	private ProfileProblem unreadable(String text) {
		ReadResult result = this.reader.read(text);
		assertThat(result).isInstanceOf(ReadResult.Unreadable.class);
		return ((ReadResult.Unreadable) result).problem();
	}

	private static ProfileProblem problem(ProblemCode code, TextLocation location) {
		return new ProfileProblem(net.yozmnews.source.domain.profile.ProfilePath.ROOT, code,
				"최상위는 항목을 담은 매핑이어야 한다", location);
	}

	private static List<String> keys(MapNode map) {
		return map.entries().stream().map(entry -> scalar(entry.key()).text()).toList();
	}

	private static ScalarNode scalar(ProfileTree node) {
		assertThat(node).isInstanceOf(ScalarNode.class);
		return (ScalarNode) node;
	}

}
