package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.LineageKind;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.domain.tagset.LineageDef;
import net.yozmnews.evaluation.domain.tagset.TagKey;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetDefinition;
import net.yozmnews.evaluation.domain.tagset.TagSetParser;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetTexts;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.AopInvocationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 버전 저장과 조회 — 실제 PostgreSQL 에서 다음 버전 · 계보 행 · 활성 끄기, 버전 목록, 원문 내보내기를 본다.
 * <p>
 * 지키는 규칙: evaluation-R3 · evaluation-R4 · evaluation-R16 · evaluation-R27
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TagSetStorageTests {

	private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

	/** 내보낸 원문 안의 중국 지역 항목 — 버전 1 의 것 */
	private static final String CHINA = "  - key: \"china\"\n    name: \"중국\"\n    description: \"중국 · 홍콩 · 대만\"\n";

	/** 내보낸 원문 안의 유럽 지역 항목 — 버전 1 의 것 */
	private static final String EUROPE = "  - key: \"europe\"\n    name: \"유럽\"\n    description: \"유럽 나라들과 유럽연합 · 러시아\"\n";

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	TagSetVersioning versioning;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcClient jdbc;

	private TagSet first;

	@BeforeEach
	void startFromTheFirstVersion() {
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
		this.first = this.tagSets.findByActiveTrue().orElseThrow();
	}

	@AfterEach
	void restoreTheFirstVersion() {
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void theStoredFirstVersionHasTheContentOfTheDefaultFile() {
		TagSetDefinition file = DefaultTagSetConfiguration.load(new ClassPathResource("tag-sets/default.yaml"),
				new TagSetParser());

		assertThat(this.first.number()).isEqualTo(1);
		assertThat(this.first.content()).isEqualTo(file.content());
		assertThat(this.first.lineage()).isEmpty();
	}

	@Test
	void theTextOfAVersionReadsBackToItsContentAndItsCurrentThresholds() {
		TagSetDefinition before = TagSetTexts.parsed(this.versioning.versionText(this.first.id()).orElseThrow());
		ThresholdChangeResult change = this.administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.6"),
				new BigDecimal("0.1"), ChangeActor.OPERATOR);
		TagSetDefinition after = TagSetTexts.parsed(this.versioning.versionText(this.first.id()).orElseThrow());

		assertThat(change).isInstanceOf(ThresholdChangeResult.Changed.class);
		assertThat(before.content()).isEqualTo(this.first.content());
		assertThat(before.initialThresholds().get(MultiTagKind.TOPIC).tagThreshold()).isEqualByComparingTo("0.5");
		assertThat(after.content()).isEqualTo(this.first.content());
		assertThat(after.initialThresholds().get(MultiTagKind.TOPIC).tagThreshold()).isEqualByComparingTo("0.6");
		assertThat(after.initialThresholds().get(MultiTagKind.REGION).tagThreshold()).isEqualByComparingTo("0.5");
		assertThat(after.lineage()).isEmpty();
	}

	@Test
	void anUnknownVersionHasNoText() {
		assertThat(this.versioning.versionText(new TagSetId(999_999))).isEmpty();
	}

	@Test
	void theNextVersionStoresItsLineageAndTheBaseIsTurnedOff() {
		String text = this.versioning.versionText(this.first.id()).orElseThrow()
			.replace("key: \"japan\"", "key: \"nippon\"")
			.replace(CHINA, "")
			.replace(EUROPE, EUROPE + "  - key: \"russia\"\n    name: \"러시아\"\n    description: \"러시아\"\n")
			+ "lineage:\n"
			+ "  - { kind: region, change: merge, from: [us, china], to: [us] }\n"
			+ "  - { kind: region, change: split, from: [europe], to: [europe, russia] }\n"
			+ "  - { kind: region, change: rename, from: [japan], to: [nippon] }\n";

		TagSetId second = activate(text);

		TagSet stored = this.tagSets.findById(second.value()).orElseThrow();
		assertThat(stored.number()).isEqualTo(2);
		assertThat(stored.active()).isTrue();
		assertThat(stored.content()).isEqualTo(TagSetTexts.parsed(text).content());
		assertThat(stored.lineage()).containsExactly(
				new LineageDef(TagKind.REGION, LineageKind.RENAME, List.of(new TagKey("japan")), List.of(new TagKey("nippon"))),
				new LineageDef(TagKind.REGION, LineageKind.SPLIT, List.of(new TagKey("europe")),
						List.of(new TagKey("europe"), new TagKey("russia"))),
				new LineageDef(TagKind.REGION, LineageKind.MERGE, List.of(new TagKey("china"), new TagKey("us")),
						List.of(new TagKey("us"))));
		// 행은 이전 키 × 새 키 — 이름 변경 하나 · 분할 둘 · 병합 둘. 원문의 항목 순서가 아니라 태그 종류 → 계보 종류 → 키 순으로 되돌린다
		assertThat(TagSetRows.count(this.jdbc, "tag_key_lineage")).isEqualTo(5);
		assertThat(this.tagSets.isActive(this.first.id().value())).isFalse();
		assertThat(this.tagSets.isActive(second.value())).isTrue();
		// 이미 꺼진 버전은 다시 끄지 않는다
		assertThat(this.tagSets.deactivate(this.first.id().value())).isFalse();
		// 버전 1 의 내용 행은 그대로다(evaluation-R4)
		assertThat(this.tagSets.findById(this.first.id().value()).orElseThrow().content())
			.isEqualTo(this.first.content());
	}

	@Test
	void versionsAreListedLatestFirst() {
		TagSetId second = activate(this.versioning.versionText(this.first.id()).orElseThrow()
			.replace("description: \"미국\"", "description: \"미국 · 미국령\""));

		assertThat(this.versioning.versions()).containsExactly(
				new TagSetVersionView(second, 2, true, ChangeActor.OPERATOR, NOW),
				new TagSetVersionView(this.first.id(), 1, false, ChangeActor.SYSTEM, this.first.createdAt()));
		assertThat(this.administration.versions()).isEqualTo(this.versioning.versions());
		assertThat(this.tagSets.findByNumberGreaterThanEqualOrderByNumber(1)).extracting(TagSet::number)
			.containsExactly(1, 2);
		assertThat(this.tagSets.findByNumberGreaterThanEqualOrderByNumber(2)).extracting(TagSet::number)
			.containsExactly(2);
	}

	@Test
	void askingWhetherAnUnknownVersionIsActiveFails() {
		// 없는 ID 는 호출자 결함이다 — 빈 결과가 원시형 boolean 에 맞지 않아 실패한다
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> this.tagSets.isActive(999_999)))
			.isInstanceOf(AopInvocationException.class);
	}

	private TagSetId activate(String text) {
		return TagSetVersions.activate(text, this.tagSets, this.thresholds,
				new TransactionTemplate(this.transactionManager), NOW);
	}

}
