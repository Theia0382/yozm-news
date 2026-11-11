package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.platform.ChangeActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 활성 체계 조회 — 실제 PostgreSQL 에서 기동 때 적재된 첫 버전과 처음 태그 기준 · 저장 기준 · 이력을 본다.
 * <p>
 * 지키는 규칙: evaluation-R2 ~ evaluation-R4 · evaluation-R9 · evaluation-R10 · evaluation-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TagSetDirectoryTests {

	@Autowired
	TagSetDirectory directory;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void startFromTheInitialThresholds() {
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void servesTheSeededFirstVersionWithQuestionSentences() {
		TagSetView tagSet = this.directory.activeTagSet();

		assertThat(tagSet.number()).isEqualTo(1);
		assertThat(tagSet.topics()).hasSize(9);
		assertThat(tagSet.topics().stream().mapToInt(topic -> topic.subtopics().size()).sum()).isEqualTo(48);
		AskedTag markets = tagSet.topics().get(1).subtopics().get(2);
		assertThat(markets.key()).isEqualTo("economy.markets");
		assertThat(markets.question())
			.isEqualTo("이 기사가 주요하게 다루는 주제가 다음에 해당하는가: 주식 · 채권 · 펀드 · ETF 시장 동향과 투자 상품(가상자산 제외)");
		assertThat(tagSet.regions().get(0).question()).isEqualTo("이 기사의 주요 무대가 다음에 해당하는가: 대한민국(북한 제외)");
		assertThat(tagSet.format().question()).isEqualTo("이 기사의 형식은 무엇인가");
		assertThat(tagSet.format().options()).hasSize(6);
		assertThat(tagSet.importance().question()).isEqualTo(
				"이 기사가 다루는 사안은 얼마나 중요한가. 등장하는 인물이나 다루는 주제가 아니라 사안이 실제로 바꾸거나 일으킨 일의 크기와 그 영향이 미치는 범위로 판단한다. "
						+ "발언 · 주장 · 반응은 그것이 일으킨 일만큼으로 본다");
		assertThat(tagSet.importance().levels()).extracting(ImportanceLevel::level).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void servesTheInitialThresholdsAndTheirFirstHistoryRows() {
		TagSetThresholds thresholds = this.directory.activeThresholds();

		assertThat(thresholds.versionId()).isEqualTo(this.directory.activeTagSet().id());
		assertThat(thresholds.topic().tagThreshold()).isEqualTo(new BigDecimal("0.500"));
		assertThat(thresholds.topic().storeThreshold()).isEqualTo(new BigDecimal("0.100"));
		assertThat(thresholds.region().tagThreshold()).isEqualTo(new BigDecimal("0.500"));
		assertThat(thresholds.region().storeThreshold()).isEqualTo(new BigDecimal("0.100"));
		List<ThresholdChangeView> changes = this.administration.thresholdChanges();
		assertThat(changes).extracting(ThresholdChangeView::kind)
			.containsExactlyInAnyOrder(MultiTagKind.TOPIC, MultiTagKind.REGION);
		assertThat(changes).allSatisfy(change -> {
			assertThat(change.previousTagThreshold()).isNull();
			assertThat(change.previousStoreThreshold()).isNull();
			assertThat(change.tagThreshold()).isEqualTo(new BigDecimal("0.500"));
			assertThat(change.storeThreshold()).isEqualTo(new BigDecimal("0.100"));
			assertThat(change.actor()).isEqualTo(ChangeActor.SYSTEM);
		});
	}

}
