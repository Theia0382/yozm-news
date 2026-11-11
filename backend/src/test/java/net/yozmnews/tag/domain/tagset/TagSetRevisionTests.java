package net.yozmnews.tag.domain.tagset;

import static net.yozmnews.tag.domain.tagset.TagSetTexts.parsed;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.tag.LineageKind;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagLineageView;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetChanges;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagThresholds;
import net.yozmnews.tag.ThresholdDifference;
import net.yozmnews.tag.domain.tagset.TagSetRevision.Revision;
import org.junit.jupiter.api.Test;

/**
 * 기준 버전 대조 · 같은 내용 판정 · 변경 목록 — 기준 원문 하나와 그것을 고친 원문으로 결과를 리터럴로 본다.
 * <p>
 * 지키는 규칙: tag-R16 · tag-R23 · tag-R24
 */
class TagSetRevisionTests {

	/** 기준 버전 — 33줄. 뒤에 붙인 계보 절의 첫 항목은 35행에서 시작한다 */
	private static final String BASE = """
			questions:
			  topic: "주제: {description}"
			  region: "지역: {description}"
			  format: 형식은 무엇인가
			  importance: 얼마나 중요한가
			thresholds:
			  topic: { tagThreshold: 0.5, storeThreshold: 0.1 }
			  region: { tagThreshold: 0.5, storeThreshold: 0.1 }
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
			  - { key: abroad, name: 해외, description: 그 밖의 나라 }
			  - { key: space, name: 우주, description: 지구 밖 }
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

	private static final String ABROAD = "  - { key: abroad, name: 해외, description: 그 밖의 나라 }\n";

	private static final String MARKETS = "      - { key: markets, name: 증권, description: 주식 시장 }\n";

	private static final TagSetThresholds BASE_THRESHOLDS = new TagSetThresholds(new TagSetId(1),
			new TagThresholds(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.1")),
			new TagThresholds(MultiTagKind.REGION, new BigDecimal("0.5"), new BigDecimal("0.1")));

	private final TagSetContent base = parsed(BASE).content();

	@Test
	void theSameContentIsUnchanged() {
		assertThat(review(BASE)).isEqualTo(new Revision.Unchanged());
	}

	@Test
	void differentThresholdsAloneAreUnchanged() {
		assertThat(review(BASE.replace("topic: { tagThreshold: 0.5,", "topic: { tagThreshold: 0.6,")))
			.isEqualTo(new Revision.Unchanged());
	}

	@Test
	void aOneCharacterDescriptionChangeIsARevisedKey() {
		assertThat(changes(BASE.replace("description: 대한민국", "description: 대한민국.")))
			.isEqualTo(changes(List.of(), List.of(), List.of(new TagRef(TagKind.REGION, "domestic")), List.of(), false, false,
					List.of()));
	}

	@Test
	void addedAndRemovedKeysWithoutLineage() {
		TagSetChanges changes = changes(BASE.replace(ABROAD, "  - { key: north-korea, name: 북한, description: 북한 }\n"));

		assertThat(changes.added()).containsExactly(new TagRef(TagKind.REGION, "north-korea"));
		assertThat(changes.removed()).containsExactly(new TagRef(TagKind.REGION, "abroad"));
		assertThat(changes.lineage()).isEmpty();
	}

	@Test
	void aRenamedKeyIsNeitherAddedNorRemoved() {
		TagSetChanges changes = changes(BASE.replace("key: abroad", "key: overseas")
				+ "lineage:\n  - { kind: region, change: rename, from: [abroad], to: [overseas] }\n");

		assertThat(changes.added()).isEmpty();
		assertThat(changes.removed()).isEmpty();
		assertThat(changes.lineage()).containsExactly(
				new TagLineageView(TagKind.REGION, LineageKind.RENAME, List.of("abroad"), List.of("overseas")));
	}

	@Test
	void aSplitMayKeepItsSourceAmongItsTargets() {
		TagSetChanges replaced = changes(BASE.replace(MARKETS,
				"      - { key: stocks, name: 주식, description: 주식 시장 }\n      - { key: bonds, name: 채권, description: 채권 시장 }\n")
				+ "lineage:\n  - { kind: topic, change: split, from: [economy.markets], to: [economy.stocks, economy.bonds] }\n");
		TagSetChanges kept = changes(BASE.replace(MARKETS,
				MARKETS + "      - { key: stocks, name: 주식, description: 주식 시장 }\n")
				+ "lineage:\n  - { kind: topic, change: split, from: [economy.markets], to: [economy.markets, economy.stocks] }\n");

		assertThat(replaced.added()).isEmpty();
		assertThat(replaced.removed()).isEmpty();
		assertThat(kept.added()).isEmpty();
		assertThat(kept.removed()).isEmpty();
		assertThat(kept.revised()).isEmpty();
	}

	@Test
	void aMergeMayKeepOneSourceAsItsTarget() {
		TagSetChanges changes = changes(BASE.replace(ABROAD, "")
				+ "lineage:\n  - { kind: region, change: merge, from: [domestic, abroad], to: [domestic] }\n");

		assertThat(changes.removed()).isEmpty();
		assertThat(changes.added()).isEmpty();
	}

	@Test
	void listsAreInKindOrderThenInTagSetOrder() {
		String space = "  - { key: space, name: 우주, description: 지구 밖 }\n";
		TagSetChanges changes = changes(BASE.replace("  - { key: domestic, name: 국내, description: 대한민국 }\n" + ABROAD, "")
			.replace(space, space + "  - { key: north-korea, name: 북한, description: 북한 }\n"
					+ "  - { key: arctic, name: 북극, description: 북극 지역 }\n")
			.replace(MARKETS, MARKETS + "      - { key: bonds, name: 채권, description: 채권 시장 }\n")
			.replace("excludedByDefault: true }\n",
					"excludedByDefault: true }\n  - { key: opinion, name: 사설, description: 의견 기사 }\n")
			.replace("description: 국제 설명", "description: 국제 소식")
			.replace("name: 국제정치", "name: 국제 정치"));

		// 추가 · 문구 변경은 새 버전의 순서, 삭제는 기준 버전의 순서다 — 키 이름 순이 아니다
		assertThat(changes.added()).containsExactly(new TagRef(TagKind.TOPIC, "economy.bonds"),
				new TagRef(TagKind.REGION, "north-korea"), new TagRef(TagKind.REGION, "arctic"),
				new TagRef(TagKind.FORMAT, "opinion"));
		assertThat(changes.removed()).containsExactly(new TagRef(TagKind.REGION, "domestic"),
				new TagRef(TagKind.REGION, "abroad"));
		assertThat(changes.revised()).containsExactly(new TagRef(TagKind.TOPIC, "world"),
				new TagRef(TagKind.TOPIC, "world.politics"));
	}

	@Test
	void questionImportanceOrderAndExclusionChanges() {
		assertThat(changes(BASE.replace("format: 형식은 무엇인가", "format: 기사의 형식은 무엇인가")))
			.isEqualTo(changes(List.of(), List.of(), List.of(), List.of(), true, false, List.of()));
		assertThat(changes(BASE.replace("description: 다섯", "description: 아주 큼")))
			.isEqualTo(changes(List.of(), List.of(), List.of(), List.of(), false, true, List.of()));
		// 순서만 바뀌어도 내용이 다르다 — 목록은 모두 빈다
		assertThat(changes(BASE.replace("  - { key: domestic, name: 국내, description: 대한민국 }\n" + ABROAD,
				ABROAD + "  - { key: domestic, name: 국내, description: 대한민국 }\n")))
			.isEqualTo(changes(List.of(), List.of(), List.of(), List.of(), false, false, List.of()));
		assertThat(changes(BASE.replace("description: 홍보 기사, excludedByDefault: true", "description: 홍보 기사")).revised())
			.containsExactly(new TagRef(TagKind.FORMAT, "press-release"));
	}

	@Test
	void thresholdsThatDifferFromTheCurrentOnesAreListedWhenTheContentChanged() {
		TagSetChanges changes = changes(BASE.replace("description: 대한민국", "description: 대한민국.")
			.replace("topic: { tagThreshold: 0.5, storeThreshold: 0.1 }", "topic: { tagThreshold: 0.60, storeThreshold: 0.1 }")
			.replace("region: { tagThreshold: 0.5, storeThreshold: 0.1 }", "region: { tagThreshold: 0.500, storeThreshold: 0.1 }"));

		// 원문의 값은 소수 셋째 자리로 맞춰진다(ThresholdValues) — 0.500 은 지금 값 0.5 와 같다
		assertThat(changes.thresholds()).containsExactly(new ThresholdDifference(
				new TagThresholds(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.1")),
				new TagThresholds(MultiTagKind.TOPIC, new BigDecimal("0.600"), new BigDecimal("0.100"))));
	}

	@Test
	void rejectsASourceThatIsNotInTheBaseVersion() {
		assertThat(problems(BASE.replace("key: abroad", "key: overseas")
				+ "lineage:\n  - { kind: region, change: rename, from: [nowhere], to: [overseas] }\n"))
			.containsExactly("35:5 lineage[0] LINEAGE_SOURCE_MISSING");
	}

	@Test
	void rejectsASourceThatStaysWithoutBeingAmongItsTargets() {
		assertThat(problems(BASE.replace(ABROAD, ABROAD + "  - { key: overseas, name: 해외 2, description: 바다 건너 }\n")
				+ "lineage:\n  - { kind: region, change: rename, from: [abroad], to: [overseas] }\n"))
			.containsExactly("36:5 lineage[0] LINEAGE_SOURCE_KEPT");
		assertThat(problems(BASE.replace(MARKETS, MARKETS + "      - { key: stocks, name: 주식, description: 주식 시장 }\n")
				+ "lineage:\n  - { kind: topic, change: split, from: [economy.politics], to: [economy.markets, economy.stocks] }\n"))
			.containsExactly("36:5 lineage[0] LINEAGE_SOURCE_KEPT", "36:5 lineage[0] LINEAGE_TARGET_EXISTS");
	}

	@Test
	void rejectsTargetsThatAreOtherKeysOfTheBaseVersion() {
		assertThat(problems(BASE.replace(ABROAD, "")
				+ "lineage:\n  - { kind: region, change: rename, from: [abroad], to: [domestic] }\n"))
			.containsExactly("34:5 lineage[0] LINEAGE_TARGET_EXISTS");
		assertThat(problems(BASE.replace(MARKETS, "      - { key: stocks, name: 주식, description: 주식 시장 }\n")
				+ "lineage:\n  - { kind: topic, change: split, from: [economy.markets], to: [economy.politics, economy.stocks] }\n"))
			.containsExactly("35:5 lineage[0] LINEAGE_TARGET_EXISTS");
		assertThat(problems(BASE.replace("  - { key: domestic, name: 국내, description: 대한민국 }\n" + ABROAD, "")
				+ "lineage:\n  - { kind: region, change: merge, from: [domestic, abroad], to: [space] }\n"))
			.containsExactly("33:5 lineage[0] LINEAGE_TARGET_EXISTS");
	}

	@Test
	void theSameContentWithALineageSectionIsRejected() {
		assertThat(review(BASE + "lineage:\n  - { kind: region, change: rename, from: [abroad], to: [domestic] }\n"))
			.isInstanceOf(Revision.Rejected.class);
		assertThat(problems(BASE + "lineage:\n  - { kind: region, change: rename, from: [abroad], to: [domestic] }\n"))
			.containsExactly("35:5 lineage[0] LINEAGE_SOURCE_KEPT", "35:5 lineage[0] LINEAGE_TARGET_EXISTS");
	}

	private Revision review(String next) {
		return TagSetRevision.review(this.base, BASE_THRESHOLDS, parsed(next));
	}

	private TagSetChanges changes(String next) {
		Revision revision = review(next);
		assertThat(revision).as("%s", revision).isInstanceOf(Revision.Changed.class);
		return ((Revision.Changed) revision).changes();
	}

	private static TagSetChanges changes(List<TagRef> added, List<TagRef> removed, List<TagRef> revised,
			List<TagLineageView> lineage, boolean questions, boolean importance, List<ThresholdDifference> thresholds) {
		return new TagSetChanges(added, removed, revised, lineage, questions, importance, thresholds);
	}

	private List<String> problems(String next) {
		Revision revision = review(next);
		assertThat(revision).as("%s", revision).isInstanceOf(Revision.Rejected.class);
		return ((Revision.Rejected) revision).problems()
			.stream()
			.map(problem -> problem.location() + " " + problem.path() + " " + problem.code())
			.toList();
	}

}
