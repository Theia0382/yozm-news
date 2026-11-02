package net.yozmnews.evaluation.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.yozmnews.evaluation.LineageKind;
import net.yozmnews.evaluation.TagKeyMapping;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagSetId;
import org.junit.jupiter.api.Test;

/**
 * 태그 키 계보 해석 — 지역 키만 바뀌는 세 버전으로, 버전마다 활성 버전 키의 집합을 수기 리터럴로 본다.
 * <p>
 * 버전 1 의 지역은 a · b · keep · gone, 버전 2 는 a 를 a1 · a2 로 나누고 gone 을 계보 없이 지운다, 버전 3 은 a1 · b 를 c 로
 * 합치고 gone 을 다시 쓴다.
 * <p>
 * 지키는 규칙: evaluation-R26
 */
class KeyLineageTests {

	private static final KeyLineage.Version V1 = version(1, List.of(), "a", "b", "keep", "gone");

	private static final KeyLineage.Version V2 = version(2,
			List.of(lineage(LineageKind.SPLIT, List.of("a"), List.of("a1", "a2"))), "a1", "a2", "b", "keep");

	private static final KeyLineage.Version V3 = version(3,
			List.of(lineage(LineageKind.MERGE, List.of("a1", "b"), List.of("c"))), "c", "a2", "keep", "gone");

	@Test
	void followsTheLineageVersionByVersionToTheLatest() {
		TagKeyMapping mapping = KeyLineage.toLatest(List.of(V1, V2, V3));

		assertThat(mapping.from()).isEqualTo(new TagSetId(1));
		assertThat(mapping.to()).isEqualTo(new TagSetId(3));
		assertThat(mapping.resolve(TagKind.REGION, "a")).containsExactlyInAnyOrder("c", "a2");
		assertThat(mapping.resolve(TagKind.REGION, "b")).containsExactly("c");
		assertThat(mapping.resolve(TagKind.REGION, "keep")).containsExactly("keep");
		// 계보 없이 지운 키는 뒤 버전이 같은 키를 다시 써도 이어지지 않는다
		assertThat(mapping.resolve(TagKind.REGION, "gone")).isEmpty();
		assertThat(mapping.keys().get(TagKind.REGION)).containsOnlyKeys("a", "b", "keep", "gone");
	}

	@Test
	void startsFromTheVersionTheKeyBelongsTo() {
		TagKeyMapping mapping = KeyLineage.toLatest(List.of(V2, V3));

		assertThat(mapping.resolve(TagKind.REGION, "a1")).containsExactly("c");
		assertThat(mapping.resolve(TagKind.REGION, "a2")).containsExactly("a2");
		assertThat(mapping.keys().get(TagKind.REGION)).containsOnlyKeys("a1", "a2", "b", "keep");
	}

	@Test
	void theLatestVersionMapsEachKeyToItself() {
		TagKeyMapping mapping = KeyLineage.toLatest(List.of(V3));

		assertThat(mapping.from()).isEqualTo(new TagSetId(3));
		assertThat(mapping.to()).isEqualTo(new TagSetId(3));
		assertThat(mapping.resolve(TagKind.REGION, "gone")).containsExactly("gone");
		assertThat(mapping.resolve(TagKind.TOPIC, "economy")).containsExactly("economy");
		assertThat(mapping.resolve(TagKind.TOPIC, "economy.markets")).containsExactly("economy.markets");
		assertThat(mapping.resolve(TagKind.FORMAT, "straight")).containsExactly("straight");
	}

	@Test
	void keysThatAreUntouchedStayAcrossVersions() {
		TagKeyMapping mapping = KeyLineage.toLatest(List.of(V1, V2, V3));

		assertThat(mapping.resolve(TagKind.TOPIC, "economy.markets")).containsExactly("economy.markets");
		assertThat(mapping.resolve(TagKind.FORMAT, "press-release")).containsExactly("press-release");
	}

	@Test
	void aLineageMovesOnlyKeysOfItsOwnKind() {
		// 지역 계보의 이전 키 straight 는 형식 키 straight 와 조각이 같다 — 형식 키는 그대로다
		TagKeyMapping mapping = KeyLineage.toLatest(List.of(version(1, List.of(), "straight"),
				version(2, List.of(lineage(LineageKind.RENAME, List.of("straight"), List.of("coast"))), "coast")));

		assertThat(mapping.resolve(TagKind.REGION, "straight")).containsExactly("coast");
		assertThat(mapping.resolve(TagKind.FORMAT, "straight")).containsExactly("straight");
	}

	@Test
	void rejectsAKeyThatIsNotInTheFromVersionAndAnEmptyChain() {
		TagKeyMapping mapping = KeyLineage.toLatest(List.of(V1, V2, V3));

		assertThatIllegalArgumentException().isThrownBy(() -> mapping.resolve(TagKind.REGION, "c"));
		assertThatIllegalArgumentException().isThrownBy(() -> mapping.resolve(TagKind.FORMAT, "a"));
		assertThatIllegalArgumentException().isThrownBy(() -> KeyLineage.toLatest(List.of()));
	}

	private static LineageDef lineage(LineageKind change, List<String> from, List<String> to) {
		return new LineageDef(TagKind.REGION, change, from.stream().map(TagKey::new).toList(),
				to.stream().map(TagKey::new).toList());
	}

	/** 주제 · 형식은 버전마다 같고 지역만 다른 버전 */
	private static KeyLineage.Version version(long id, List<LineageDef> lineage, String... regions) {
		String regionLines = Arrays.stream(regions)
			.map(key -> "  - { key: " + key + ", name: " + key + ", description: " + key + " 지역 }")
			.collect(Collectors.joining("\n"));
		String text = """
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
				      - { key: markets, name: 증권, description: 주식 시장 }
				regions:
				%s
				formats:
				  - { key: straight, name: 스트레이트, description: 일반 보도 }
				  - { key: press-release, name: 보도자료, description: 홍보 기사 }
				importance:
				  - { level: 1, description: 하나 }
				  - { level: 2, description: 둘 }
				  - { level: 3, description: 셋 }
				  - { level: 4, description: 넷 }
				  - { level: 5, description: 다섯 }
				""".formatted(regionLines);
		TagSetParse parse = new TagSetParser().parse(text);
		assertThat(parse).as("%s", parse).isInstanceOf(TagSetParse.Parsed.class);
		return new KeyLineage.Version(new TagSetId(id), ((TagSetParse.Parsed) parse).definition().content(), lineage);
	}

}
