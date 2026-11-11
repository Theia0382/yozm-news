package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.domain.tagset.TagSet;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 태그 키 계보 조회 — 실제 PostgreSQL 에 저장한 세 버전(1 기본 체계 → 2 japan 을 nippon 으로 → 3 nippon 을 jp 로 · europe 을 계보
 * 없이 지움)으로 활성 버전 키의 표를 본다.
 * <p>
 * 지키는 규칙: tag-R26
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TagKeyLineageServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

	private static final String EUROPE = "  - key: \"europe\"\n    name: \"유럽\"\n    description: \"유럽 나라들과 유럽연합 · 러시아\"\n";

	@Autowired
	TagKeyLineage lineage;

	@Autowired
	TagSetVersioning versioning;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcClient jdbc;

	private TagSetId first;

	private TagSetId second;

	private TagSetId third;

	@BeforeEach
	void storeThreeVersions() {
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		this.first = this.tagSets.findByActiveTrue().map(TagSet::id).orElseThrow();
		this.second = activate(this.versioning.versionText(this.first).orElseThrow()
			.replace("key: \"japan\"", "key: \"nippon\"")
				+ "lineage:\n  - { kind: region, change: rename, from: [japan], to: [nippon] }\n");
		this.third = activate(this.versioning.versionText(this.second).orElseThrow()
			.replace("key: \"nippon\"", "key: \"jp\"")
			.replace(EUROPE, "")
				+ "lineage:\n  - { kind: region, change: rename, from: [nippon], to: [jp] }\n");
	}

	@AfterEach
	void restoreTheFirstVersion() {
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
	}

	@Test
	void keysOfTheFirstVersionFollowTheLineageToTheActiveVersion() {
		TagKeyMapping mapping = this.lineage.toActive(this.first);

		assertThat(mapping.from()).isEqualTo(this.first);
		assertThat(mapping.to()).isEqualTo(this.third);
		assertThat(mapping.resolve(TagKind.REGION, "japan")).containsExactly("jp");
		assertThat(mapping.resolve(TagKind.REGION, "europe")).isEmpty();
		assertThat(mapping.resolve(TagKind.REGION, "us")).containsExactly("us");
		assertThat(mapping.resolve(TagKind.TOPIC, "politics")).containsExactly("politics");
		assertThat(mapping.resolve(TagKind.TOPIC, "politics.executive")).containsExactly("politics.executive");
	}

	@Test
	void keysOfALaterVersionStartFromThatVersion() {
		assertThat(this.lineage.toActive(this.second).resolve(TagKind.REGION, "nippon")).containsExactly("jp");
		assertThat(this.lineage.toActive(this.third).resolve(TagKind.REGION, "jp")).containsExactly("jp");
		assertThat(this.lineage.toActive(this.third).from()).isEqualTo(this.third);
	}

	@Test
	void anUnknownVersionIsACallerDefect() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.lineage.toActive(new TagSetId(999_999)));
	}

	private TagSetId activate(String text) {
		return TagSetVersions.activate(text, this.tagSets, this.thresholds,
				new TransactionTemplate(this.transactionManager), NOW);
	}

}
