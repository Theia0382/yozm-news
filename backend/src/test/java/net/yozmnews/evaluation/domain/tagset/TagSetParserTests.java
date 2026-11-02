package net.yozmnews.evaluation.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.evaluation.LineageKind;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import net.yozmnews.platform.StrictYamlBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 태그 체계 파일의 읽기 · 검증 — 합성 체계 원문으로 정의와 문제(경로 · 종류)를 본다.
 * <p>
 * 지키는 규칙: evaluation-R5 ~ evaluation-R8 · evaluation-R10 · evaluation-R22
 */
class TagSetParserTests {

	/** 대분류 2 · 중분류 3 · 지역 2 · 형식 2 · 중요도 5 — 두 대분류가 같은 중분류 조각 politics 를 갖는다 */
	private static final String VALID = """
			questions:
			  topic: "주제: {description}"
			  region: "지역: {description}"
			  format: 형식은 무엇인가
			  importance: 얼마나 중요한가
			thresholds:
			  topic: { tagThreshold: 0.5, storeThreshold: 0.1 }
			  region: { tagThreshold: 0.6, storeThreshold: 0.2 }
			topics:
			  - key: economy
			    name: 경제
			    description: 경제 설명
			    subtopics:
			      - { key: politics, name: 경제 정치, description: 경제 정책 }
			      - { key: markets, name: 증권, description: 주식 시장 }
			  - key: world
			    name: 국제
			    description: 국제 설명
			    subtopics:
			      - { key: politics, name: 국제정치, description: 외국 정부의 정치 }
			regions:
			  - { key: domestic, name: 국내, description: 대한민국 }
			  - { key: other-foreign, name: 기타 해외, description: 그 밖의 해외 }
			formats:
			  - { key: straight, name: 스트레이트, description: 일반 보도 }
			  - { key: press-release, name: 보도자료, description: 홍보 기사, excludedByDefault: true }
			importance:
			  - { level: 1, description: 하나 }
			  - { level: 2, description: 둘 }
			  - { level: 3, description: 셋 }
			  - { level: 4, description: 넷 }
			  - { level: 5, description: 다섯 }
			""";

	/** VALID 뒤에 붙이는 계보 절 — 항목 셋, 새 키는 모두 VALID 에 있다. VALID 가 32줄이라 항목은 34 · 38 · 39행에서 시작한다 */
	private static final String LINEAGE = """
			lineage:
			  - kind: topic
			    change: split
			    from: [economy.trade]
			    to: [economy.politics, economy.markets]
			  - { kind: region, change: rename, from: [overseas], to: [other-foreign] }
			  - { kind: format, change: merge, from: [news, brief], to: [straight] }
			""";

	private final TagSetParser parser = new TagSetParser();

	@Test
	void readsAValidTagSetInFileOrder() {
		TagSetDefinition definition = parsed(VALID);
		TagSetContent content = definition.content();

		assertThat(content.topicTemplate().text()).isEqualTo("주제: {description}");
		assertThat(content.regionTemplate().text()).isEqualTo("지역: {description}");
		assertThat(content.formatQuestion()).isEqualTo("형식은 무엇인가");
		assertThat(content.importanceQuestion()).isEqualTo("얼마나 중요한가");
		assertThat(content.topics()).extracting(topic -> topic.key().value()).containsExactly("economy", "world");
		assertThat(content.topics().get(0).subtopics()).extracting(tag -> tag.key().value(), TagDef::name)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("economy.politics", "경제 정치"),
					org.assertj.core.groups.Tuple.tuple("economy.markets", "증권"));
		assertThat(content.topics().get(1).subtopics()).extracting(tag -> tag.key().value())
			.containsExactly("world.politics");
		assertThat(content.regions()).extracting(tag -> tag.key().value(), TagDef::description)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("domestic", "대한민국"),
					org.assertj.core.groups.Tuple.tuple("other-foreign", "그 밖의 해외"));
		assertThat(content.formats()).extracting(format -> format.key().value(), FormatDef::excludedByDefault)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("straight", false),
					org.assertj.core.groups.Tuple.tuple("press-release", true));
		assertThat(content.importance()).extracting(ImportanceLevelDef::level, ImportanceLevelDef::description)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(1, "하나"), org.assertj.core.groups.Tuple.tuple(2, "둘"),
					org.assertj.core.groups.Tuple.tuple(3, "셋"), org.assertj.core.groups.Tuple.tuple(4, "넷"),
					org.assertj.core.groups.Tuple.tuple(5, "다섯"));
		assertThat(definition.initialThresholds()).containsOnly(
				org.assertj.core.api.Assertions.entry(MultiTagKind.TOPIC,
						new ThresholdValues(new BigDecimal("0.5"), new BigDecimal("0.1"))),
				org.assertj.core.api.Assertions.entry(MultiTagKind.REGION,
						new ThresholdValues(new BigDecimal("0.6"), new BigDecimal("0.2"))));
	}

	@Test
	void hasNoLineageWithoutALineageSection() {
		assertThat(parsed(VALID).lineage()).isEmpty();
	}

	@Test
	void exposesTheKeysOfEachKindInTagSetOrder() {
		TagSetContent content = parsed(VALID).content();

		assertThat(content.keys(TagKind.TOPIC)).containsExactly("economy", "economy.politics", "economy.markets", "world",
				"world.politics");
		assertThat(content.keys(TagKind.REGION)).containsExactly("domestic", "other-foreign");
		assertThat(content.keys(TagKind.FORMAT)).containsExactly("straight", "press-release");
	}

	@Test
	void readsLineageEntriesInOrderWithTheirPlaces() {
		List<StrictYamlBinding.Located<LineageDef>> lineage = parsed(VALID + LINEAGE).lineage();

		assertThat(lineage).extracting(entry -> entry.path().toString(), entry -> entry.location().line())
			.containsExactly(org.assertj.core.groups.Tuple.tuple("lineage[0]", 34),
					org.assertj.core.groups.Tuple.tuple("lineage[1]", 38),
					org.assertj.core.groups.Tuple.tuple("lineage[2]", 39));
		assertThat(lineage.get(0).value()).isEqualTo(new LineageDef(TagKind.TOPIC, LineageKind.SPLIT,
				List.of(new TagKey("economy.trade")), List.of(new TagKey("economy.politics"), new TagKey("economy.markets"))));
		assertThat(lineage.get(1).value()).isEqualTo(new LineageDef(TagKind.REGION, LineageKind.RENAME,
				List.of(new TagKey("overseas")), List.of(new TagKey("other-foreign"))));
		assertThat(lineage.get(2).value()).isEqualTo(new LineageDef(TagKind.FORMAT, LineageKind.MERGE,
				List.of(new TagKey("news"), new TagKey("brief")), List.of(new TagKey("straight"))));
	}

	@Test
	void rejectsLineageKindsAndChangesOutsideTheClosedValues() {
		assertThat(problems(VALID + LINEAGE.replace("kind: region", "kind: place")))
			.containsExactly("lineage[1].kind INVALID_LINEAGE_VALUE");
		assertThat(problems(VALID + LINEAGE.replace("change: merge", "change: join")))
			.containsExactly("lineage[2].change INVALID_LINEAGE_VALUE");
		assertThat(problems(VALID + LINEAGE.replace("kind: topic", "kind: TOPIC")))
			.containsExactly("lineage[0].kind INVALID_LINEAGE_VALUE");
	}

	@Test
	void rejectsLineageCountsThatDoNotFitTheChange() {
		assertThat(problems(VALID + LINEAGE.replace("to: [other-foreign]", "to: [other-foreign, domestic]")))
			.containsExactly("lineage[1] INVALID_LINEAGE_COUNT");
		assertThat(problems(VALID + LINEAGE.replace("to: [economy.politics, economy.markets]", "to: [economy.politics]")))
			.containsExactly("lineage[0] INVALID_LINEAGE_COUNT");
		assertThat(problems(VALID + LINEAGE.replace("from: [news, brief]", "from: [news]")))
			.containsExactly("lineage[2] INVALID_LINEAGE_COUNT");
		assertThat(problems(VALID + LINEAGE.replace("from: [overseas]", "from: []")))
			.containsExactly("lineage[1] INVALID_LINEAGE_COUNT");
	}

	@Test
	void rejectsTheSameKeyTwiceWithinAnEntry() {
		assertThat(problems(VALID + LINEAGE.replace("from: [news, brief]", "from: [news, news]")))
			.containsExactly("lineage[2].from[1] DUPLICATE_LINEAGE_KEY");
		assertThat(problems(VALID + LINEAGE.replace("from: [overseas]", "from: [other-foreign]")))
			.containsExactly("lineage[1] DUPLICATE_LINEAGE_KEY");
	}

	@Test
	void rejectsAKeyThatIsTheSourceOrTargetOfTwoEntriesOfAKind() {
		assertThat(problems(VALID + LINEAGE + "  - { kind: format, change: rename, from: [brief], to: [press-release] }\n"))
			.containsExactly("lineage[3].from[0] LINEAGE_KEY_REPEATED");
		assertThat(problems(VALID + LINEAGE + "  - { kind: format, change: rename, from: [notice], to: [straight] }\n"))
			.containsExactly("lineage[3].to[0] LINEAGE_KEY_REPEATED");
		// 다른 태그 종류의 같은 키 조각은 겹치지 않는다
		assertThat(parsed(VALID + LINEAGE + "  - { kind: region, change: rename, from: [brief], to: [domestic] }\n")
			.lineage()).hasSize(4);
	}

	@Test
	void rejectsATargetThatIsNotInTheText() {
		assertThat(problems(VALID + LINEAGE.replace("to: [other-foreign]", "to: [abroad]")))
			.containsExactly("lineage[1].to[0] LINEAGE_TARGET_MISSING");
		// 중분류 조각만 적은 키는 대분류 키로 읽혀 원문에 없다 — 중분류는 전체 키로 적는다
		assertThat(problems(VALID + LINEAGE.replace("from: [economy.trade]", "from: [trade]")
			.replace("to: [economy.politics, economy.markets]", "to: [politics, markets]")))
			.containsExactly("lineage[0].to[0] LINEAGE_TARGET_MISSING", "lineage[0].to[1] LINEAGE_TARGET_MISSING");
	}

	@Test
	void doesNotLookForTargetsWhenTheRestOfTheTextHasProblems() {
		assertThat(problems(VALID.replace("name: 국내", "name: \" \"") + LINEAGE.replace("to: [other-foreign]", "to: [abroad]")))
			.containsExactly("regions[0].name BLANK");
	}

	@Test
	void linksTopicCategoriesOnlyToCategoriesAndSubtopicsOnlyToSubtopics() {
		assertThat(problems(VALID + LINEAGE.replace("from: [economy.trade]", "from: [trade]")))
			.containsExactly("lineage[0] LINEAGE_LAYER_MISMATCH");
		assertThat(problems(VALID + "lineage:\n  - { kind: topic, change: rename, from: [business], to: [world.politics] }\n"))
			.containsExactly("lineage[0] LINEAGE_LAYER_MISMATCH");
		// 이전 키끼리 층이 섞여도 막는다
		assertThat(problems(VALID + "lineage:\n"
				+ "  - { kind: topic, change: merge, from: [business, world.politics], to: [economy] }\n"))
			.containsExactly("lineage[0] LINEAGE_LAYER_MISMATCH");
		// 대분류끼리는 잇는다
		assertThat(parsed(VALID + "lineage:\n  - { kind: topic, change: rename, from: [business], to: [economy] }\n")
			.lineage()).hasSize(1);
	}

	@Test
	void rejectsLineageKeysThatAreNotKeysOfTheirKind() {
		assertThat(problems(VALID + LINEAGE.replace("from: [overseas]", "from: [Overseas]")))
			.containsExactly("lineage[1].from[0] INVALID_KEY");
		assertThat(problems(VALID + LINEAGE.replace("from: [overseas]", "from: [world.overseas]")))
			.containsExactly("lineage[1].from[0] INVALID_KEY");
		assertThat(problems(VALID + LINEAGE.replace("from: [economy.trade]", "from: [economy.trade.old]")))
			.containsExactly("lineage[0].from[0] INVALID_KEY");
	}

	@Test
	void rejectsALineageEntryWithoutItsFields() {
		assertThat(problems(VALID + "lineage:\n  - { kind: region, change: rename, from: [overseas] }\n"))
			.containsExactly("lineage[0].to MISSING_REQUIRED");
		assertThat(problems(VALID + "lineage:\n  - { kind: region, from: [overseas], to: [domestic] }\n"))
			.containsExactly("lineage[0].change MISSING_REQUIRED");
		assertThat(problems(VALID + "lineage:\n  - { kind: region, change: rename, from: [overseas], to: [domestic], why: x }\n"))
			.containsExactly("lineage[0].why UNKNOWN_KEY");
	}

	@Test
	void acceptsAFortyCharacterKeyAndAThresholdWithTrailingZeros() {
		String key = "a".repeat(40);
		TagSetDefinition definition = parsed(VALID.replace("key: domestic", "key: " + key)
			.replace("tagThreshold: 0.5,", "tagThreshold: 0.5000,"));

		assertThat(definition.content().regions().get(0).key().value()).isEqualTo(key);
		assertThat(definition.initialThresholds().get(MultiTagKind.TOPIC).tagThreshold())
			.isEqualByComparingTo(new BigDecimal("0.5"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "A", "a_b", "a..b", "a.b", "-a", "a--b", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
	void rejectsAKeyThatIsNotASegment(String key) {
		assertThat(problems(VALID.replace("key: domestic", "key: \"" + key + "\"")))
			.containsExactly("regions[0].key INVALID_KEY");
	}

	@Test
	void rejectsDuplicateKeysWithinAKind() {
		assertThat(problems(VALID.replace("key: world", "key: economy"))).containsExactly("topics[1].key DUPLICATE_KEY_IN_KIND");
		assertThat(problems(VALID.replace("key: markets", "key: politics")))
			.containsExactly("topics[0].subtopics[1].key DUPLICATE_KEY_IN_KIND");
		assertThat(problems(VALID.replace("key: other-foreign", "key: domestic")))
			.containsExactly("regions[1].key DUPLICATE_KEY_IN_KIND");
		assertThat(problems(VALID.replace("key: press-release", "key: straight")))
			.containsExactly("formats[1].key DUPLICATE_KEY_IN_KIND");
	}

	@Test
	void rejectsKindsWithTooFewTags() {
		assertThat(problems(VALID.substring(0, VALID.indexOf("topics:")) + "topics: []\n"
				+ VALID.substring(VALID.indexOf("regions:")))).containsExactly("topics NO_TOPICS");
		assertThat(problems(VALID.replace("""
				    subtopics:
				      - { key: politics, name: 국제정치, description: 외국 정부의 정치 }
				""", "    subtopics: []\n"))).containsExactly("topics[1].subtopics NO_SUBTOPICS");
		assertThat(problems(VALID.substring(0, VALID.indexOf("regions:")) + "regions: []\n"
				+ VALID.substring(VALID.indexOf("formats:")))).containsExactly("regions NO_REGIONS");
		assertThat(problems(VALID.replace("  - { key: press-release, name: 보도자료, description: 홍보 기사, excludedByDefault: true }\n",
				""))).containsExactly("formats TOO_FEW_FORMATS");
	}

	@Test
	void rejectsImportanceLevelsThatAreNotOneToFiveOnceEach() {
		assertThat(problems(VALID.replace("  - { level: 5, description: 다섯 }\n", "")))
			.containsExactly("importance INVALID_IMPORTANCE_LEVELS");
		assertThat(problems(VALID.replace("  - { level: 1, description: 하나 }\n",
				"  - { level: 1, description: 하나 }\n  - { level: 1, description: 또 하나 }\n")))
			.containsExactly("importance INVALID_IMPORTANCE_LEVELS");
		assertThat(problems(VALID.replace("{ level: 5, description: 다섯 }", "{ level: 0, description: 영 }")))
			.containsExactly("importance INVALID_IMPORTANCE_LEVELS");
	}

	@Test
	void rejectsTextThatIsBlankMultiLineOrTooLong() {
		assertThat(problems(VALID.replace("name: 국내", "name: \" \""))).containsExactly("regions[0].name BLANK");
		assertThat(problems(VALID.replace("description: 대한민국", "description: \"대한\\n민국\"")))
			.containsExactly("regions[0].description MULTI_LINE");
		assertThat(problems(VALID.replace("name: 국내", "name: " + "가".repeat(41))))
			.containsExactly("regions[0].name TOO_LONG");
		assertThat(problems(VALID.replace("description: 대한민국", "description: " + "가".repeat(121))))
			.containsExactly("regions[0].description TOO_LONG");
		assertThat(problems(VALID.replace("format: 형식은 무엇인가", "format: " + "가".repeat(201))))
			.containsExactly("questions.format TOO_LONG");
		assertThat(problems(VALID.replace("{ level: 3, description: 셋 }", "{ level: 3, description: \"" + "가".repeat(121) + "\" }")))
			.containsExactly("importance[2].description TOO_LONG");
	}

	@Test
	void acceptsTextAtTheLengthLimits() {
		TagSetDefinition definition = parsed(VALID.replace("name: 국내", "name: " + "가".repeat(40))
			.replace("description: 대한민국", "description: " + "가".repeat(120))
			.replace("format: 형식은 무엇인가", "format: " + "가".repeat(200)));

		assertThat(definition.content().regions().get(0).name()).hasSize(40);
	}

	@Test
	void rejectsTemplatesWithoutExactlyOnePlaceholderAndQuestionsWithOne() {
		assertThat(problems(VALID.replace("topic: \"주제: {description}\"", "topic: 주제인가")))
			.containsExactly("questions.topic INVALID_TEMPLATE");
		assertThat(problems(VALID.replace("region: \"지역: {description}\"", "region: \"{description} {description}\"")))
			.containsExactly("questions.region INVALID_TEMPLATE");
		assertThat(problems(VALID.replace("format: 형식은 무엇인가", "format: \"{description} 인가\"")))
			.containsExactly("questions.format PLACEHOLDER_NOT_ALLOWED");
		assertThat(problems(VALID.replace("importance: 얼마나 중요한가", "importance: \"{description} 인가\"")))
			.containsExactly("questions.importance PLACEHOLDER_NOT_ALLOWED");
	}

	@Test
	void rejectsThresholdsOutOfRange() {
		assertThat(problems(VALID.replace("tagThreshold: 0.5,", "tagThreshold: 1.5,")))
			.containsExactly("thresholds.topic.tagThreshold INVALID_THRESHOLD");
		assertThat(problems(VALID.replace("storeThreshold: 0.2", "storeThreshold: 0.1234")))
			.containsExactly("thresholds.region.storeThreshold INVALID_THRESHOLD");
		assertThat(problems(VALID.replace("storeThreshold: 0.2", "storeThreshold: -0.001")))
			.containsExactly("thresholds.region.storeThreshold INVALID_THRESHOLD");
		assertThat(problems(VALID.replace("tagThreshold: 0.5,", "tagThreshold: .inf,")))
			.containsExactly("thresholds.topic.tagThreshold WRONG_TYPE");
	}

	@Test
	void rejectsMissingRequiredEntries() {
		assertThat(problems(VALID.substring(0, VALID.indexOf("formats:")) + VALID.substring(VALID.indexOf("\nimportance:") + 1)))
			.containsExactly("formats MISSING_REQUIRED");
		assertThat(problems(VALID.replace("  region: { tagThreshold: 0.6, storeThreshold: 0.2 }\n", "")))
			.containsExactly("thresholds.region MISSING_REQUIRED");
		assertThat(problems(VALID.replace("{ key: domestic, name: 국내, description: 대한민국 }", "{ key: domestic, name: 국내 }")))
			.containsExactly("regions[0].description MISSING_REQUIRED");
		assertThat(problems(VALID.replace("  importance: 얼마나 중요한가\n", "")))
			.containsExactly("questions.importance MISSING_REQUIRED");
	}

	@Test
	void carriesCommonYamlProblemsOverAsCodesOfTheSameName() {
		assertThat(problems(VALID + "regions: []\n")).containsExactly("regions DUPLICATE_KEY");
		assertThat(problems(VALID.replace("name: 국내", "name: &n 국내").replace("name: 기타 해외", "name: *n")))
			.containsExactly("regions[0].name ANCHOR_NOT_ALLOWED", "regions[1].name ALIAS_NOT_ALLOWED");
		assertThat(problems(VALID.replace("key: domestic,", "key: domestic, colour: red,")))
			.containsExactly("regions[0].colour UNKNOWN_KEY");
	}

	@Test
	void collectsEveryProblemInTheOrderItWasFound() {
		assertThat(problems(VALID.replace("key: domestic", "key: Domestic").replace("name: 기타 해외", "name: \" \"")))
			.containsExactly("regions[0].key INVALID_KEY", "regions[1].name BLANK");
	}

	@Test
	void readsTextUpToTheLengthLimitAndRejectsLongerText() {
		String atLimit = VALID + "#" + "x".repeat(65_536 - VALID.length() - 2) + "\n";

		assertThat(atLimit).hasSize(65_536);
		assertThat(parsed(atLimit).content().regions()).hasSize(2);
		assertThat(problems(atLimit + "#")).containsExactly(" TOO_LARGE");
	}

	private TagSetDefinition parsed(String text) {
		TagSetParse parse = this.parser.parse(text);
		assertThat(parse).as("%s", parse).isInstanceOf(TagSetParse.Parsed.class);
		return ((TagSetParse.Parsed) parse).definition();
	}

	private List<String> problems(String text) {
		TagSetParse parse = this.parser.parse(text);
		assertThat(parse).isInstanceOf(TagSetParse.Invalid.class);
		return ((TagSetParse.Invalid) parse).problems().stream().map(problem -> problem.path() + " " + problem.code())
			.toList();
	}

}
