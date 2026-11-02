package net.yozmnews.evaluation.domain.tagset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagThresholds;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 원문 쓰기 — 작은 체계의 출력 전문 리터럴과, 다시 읽었을 때 내용 · 처음 태그 기준 · 저장 기준이 같은지(왕복)를 본다.
 * <p>
 * 지키는 규칙: evaluation-R27
 */
class TagSetWriterTests {

	/** 대분류 1 · 중분류 2 · 지역 1 · 형식 2 · 중요도 5 */
	private static final String SMALL = """
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
			regions:
			  - { key: domestic, name: 국내, description: 대한민국 }
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

	private final TagSetParser parser = new TagSetParser();

	@Test
	void writesTheContentAndTheCurrentThresholdsInTheFileLayout() {
		String text = TagSetWriter.write(parsed(SMALL).content(),
				thresholds("0.600", "0.20", "0", "1.000"));

		assertThat(text).isEqualTo("""
				questions:
				  topic: "주제: {description}"
				  region: "지역: {description}"
				  format: "형식은 무엇인가"
				  importance: "얼마나 중요한가"
				thresholds:
				  topic:
				    tagThreshold: 0.6
				    storeThreshold: 0.2
				  region:
				    tagThreshold: 0
				    storeThreshold: 1
				topics:
				  - key: "economy"
				    name: "경제"
				    description: "경제 설명"
				    subtopics:
				      - key: "politics"
				        name: "경제 정치"
				        description: "경제 정책"
				      - key: "markets"
				        name: "증권"
				        description: "주식 시장"
				regions:
				  - key: "domestic"
				    name: "국내"
				    description: "대한민국"
				formats:
				  - key: "straight"
				    name: "스트레이트"
				    description: "일반 보도"
				  - key: "press-release"
				    name: "보도자료"
				    description: "홍보 기사"
				    excludedByDefault: true
				importance:
				  - level: 1
				    description: "하나"
				  - level: 2
				    description: "둘"
				  - level: 3
				    description: "셋"
				  - level: 4
				    description: "넷"
				  - level: 5
				    description: "다섯"
				""");
	}

	@Test
	void writtenTextReadsBackToTheSameContentAndThresholds() {
		TagSetDefinition small = parsed(SMALL);
		TagSetDefinition back = parsed(TagSetWriter.write(small.content(), thresholds("0.600", "0.20", "0", "1.000")));

		assertThat(back.content()).isEqualTo(small.content());
		assertThat(back.initialThresholds().get(MultiTagKind.TOPIC).tagThreshold()).isEqualByComparingTo("0.6");
		assertThat(back.initialThresholds().get(MultiTagKind.TOPIC).storeThreshold()).isEqualByComparingTo("0.2");
		assertThat(back.initialThresholds().get(MultiTagKind.REGION).tagThreshold()).isEqualByComparingTo("0");
		assertThat(back.initialThresholds().get(MultiTagKind.REGION).storeThreshold()).isEqualByComparingTo("1");
		assertThat(back.lineage()).isEmpty();
	}

	@Test
	void theDefaultFileReadsBackToTheSameContent() throws IOException {
		String file = new ClassPathResource("tag-sets/default.yaml").getContentAsString(StandardCharsets.UTF_8);
		TagSetDefinition definition = parsed(file);

		TagSetDefinition back = parsed(TagSetWriter.write(definition.content(), thresholds("0.5", "0.1", "0.5", "0.1")));

		assertThat(back.content()).isEqualTo(definition.content());
	}

	@Test
	void stringsThatYamlWouldReadOtherwiseReadBackUnchanged() {
		TagSetDefinition tricky = parsed(SMALL.replace("key: domestic", "key: \"null\"")
			.replace("name: 국내", "name: \"따옴표 \\\" 역슬래시 \\\\ 탭\\t 제어\\u0001 지움\\u007F 이모지 😀 BOM\\uFEFF\"")
			.replace("key: straight", "key: \"true\"")
			.replace("key: press-release", "key: \"123\""));

		String text = TagSetWriter.write(tricky.content(), thresholds("0.5", "0.1", "0.5", "0.1"));

		assertThat(text).contains("  - key: \"null\"\n", "  - key: \"true\"\n", "  - key: \"123\"\n",
				"    name: \"따옴표 \\\" 역슬래시 \\\\ 탭\\u0009 제어\\u0001 지움\\u007F 이모지 😀 BOM\\uFEFF\"\n");
		assertThat(parsed(text).content()).isEqualTo(tricky.content());
	}

	@Test
	void numbersDropTrailingZeros() {
		assertThat(TagSetWriter.number(new BigDecimal("0.500"))).isEqualTo("0.5");
		assertThat(TagSetWriter.number(new BigDecimal("0.125"))).isEqualTo("0.125");
		assertThat(TagSetWriter.number(new BigDecimal("0.000"))).isEqualTo("0");
		assertThat(TagSetWriter.number(new BigDecimal("1.000"))).isEqualTo("1");
	}

	private static TagSetThresholds thresholds(String topicTag, String topicStore, String regionTag,
			String regionStore) {
		return new TagSetThresholds(new TagSetId(1),
				new TagThresholds(MultiTagKind.TOPIC, new BigDecimal(topicTag), new BigDecimal(topicStore)),
				new TagThresholds(MultiTagKind.REGION, new BigDecimal(regionTag), new BigDecimal(regionStore)));
	}

	private TagSetDefinition parsed(String text) {
		TagSetParse parse = this.parser.parse(text);
		assertThat(parse).as("%s", parse).isInstanceOf(TagSetParse.Parsed.class);
		return ((TagSetParse.Parsed) parse).definition();
	}

}
