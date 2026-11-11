package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.domain.tagset.FormatDef;
import net.yozmnews.tag.domain.tagset.ImportanceLevelDef;
import net.yozmnews.tag.domain.tagset.TagDef;
import net.yozmnews.tag.domain.tagset.TagSetContent;
import net.yozmnews.tag.domain.tagset.TagSetDefinition;
import net.yozmnews.tag.domain.tagset.TagSetParser;
import net.yozmnews.tag.domain.tagset.TopicCategoryDef;
import net.yozmnews.tag.domain.threshold.ThresholdValues;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 저장소의 기본 체계 파일 — 검증을 통과하고 첫 체계(대분류 9 · 중분류 48 · 지역 7 · 형식 6 · 중요도 1 ~ 5)가 승인한 키 그대로인지 본다.
 * 기대값은 손으로 쓴 목록이다.
 * <p>
 * 지키는 규칙: tag-R1 · tag-R5 ~ tag-R8 · tag-R10
 */
class DefaultTagSetFileTests {

	private final TagSetDefinition definition = DefaultTagSetConfiguration
		.load(new ClassPathResource("tag-sets/default.yaml"), new TagSetParser());

	@Test
	void hasTheApprovedTopicKeys() {
		List<TopicCategoryDef> topics = this.definition.content().topics();

		assertThat(topics).extracting(topic -> topic.key().value())
			.containsExactly("politics", "economy", "industry", "tech", "society", "environment", "world", "culture",
					"sports");
		assertThat(topics.stream().flatMap(topic -> topic.subtopics().stream()).map(tag -> tag.key().value()))
			.containsExactly("politics.executive", "politics.legislature", "politics.north-korea",
					"politics.diplomacy-defense", "economy.macro", "economy.finance", "economy.markets",
					"economy.real-estate", "economy.consumer", "economy.crypto", "industry.semiconductor",
					"industry.mobility", "industry.energy", "industry.heavy", "industry.bio", "industry.retail",
					"industry.startup", "industry.corporate", "industry.healthcare", "tech.ai", "tech.internet",
					"tech.devices",
					"tech.telecom-media", "tech.security", "tech.game", "tech.science", "society.incidents",
					"society.law", "society.education", "society.labor", "society.welfare", "society.health-policy",
					"society.regional", "environment.climate", "environment.ecology", "world.politics",
					"world.conflict", "world.society", "culture.health", "culture.leisure", "culture.lifestyle",
					"culture.arts", "culture.religion", "culture.entertainment", "culture.family", "sports.baseball",
					"sports.football", "sports.others");
	}

	@Test
	void hasTheApprovedRegionsFormatsAndImportanceLevels() {
		TagSetContent content = this.definition.content();

		assertThat(content.regions()).extracting(tag -> tag.key().value(), TagDef::name)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("domestic", "국내"),
					org.assertj.core.groups.Tuple.tuple("korean-peninsula", "북한·한반도"),
					org.assertj.core.groups.Tuple.tuple("us", "미국"), org.assertj.core.groups.Tuple.tuple("china", "중국"),
					org.assertj.core.groups.Tuple.tuple("japan", "일본"),
					org.assertj.core.groups.Tuple.tuple("europe", "유럽"),
					org.assertj.core.groups.Tuple.tuple("other-foreign", "기타 해외"));
		assertThat(content.formats()).extracting(format -> format.key().value(), FormatDef::excludedByDefault)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("straight", false),
					org.assertj.core.groups.Tuple.tuple("analysis", false),
					org.assertj.core.groups.Tuple.tuple("opinion", false),
					org.assertj.core.groups.Tuple.tuple("interview", false),
					org.assertj.core.groups.Tuple.tuple("press-release", true),
					org.assertj.core.groups.Tuple.tuple("brief", true));
		assertThat(content.importance()).extracting(ImportanceLevelDef::level).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void hasTheApprovedQuestionsAndInitialThresholds() {
		TagSetContent content = this.definition.content();

		assertThat(content.topicTemplate().text()).isEqualTo("이 기사가 주요하게 다루는 주제가 다음에 해당하는가: {description}");
		assertThat(content.regionTemplate().text()).isEqualTo("이 기사의 주요 무대가 다음에 해당하는가: {description}");
		assertThat(content.formatQuestion()).isEqualTo("이 기사의 형식은 무엇인가");
		assertThat(content.importanceQuestion()).isEqualTo(
				"이 기사가 다루는 사안은 얼마나 중요한가. 등장하는 인물이나 다루는 주제가 아니라 사안이 실제로 바꾸거나 일으킨 일의 크기와 그 영향이 미치는 범위로 판단한다. "
						+ "발언 · 주장 · 반응은 그것이 일으킨 일만큼으로 본다");
		assertThat(this.definition.initialThresholds()).containsOnly(
				entry(MultiTagKind.TOPIC, new ThresholdValues(new BigDecimal("0.5"), new BigDecimal("0.1"))),
				entry(MultiTagKind.REGION, new ThresholdValues(new BigDecimal("0.5"), new BigDecimal("0.1"))));
	}

}
