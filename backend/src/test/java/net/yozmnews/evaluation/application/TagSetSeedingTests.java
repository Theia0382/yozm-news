package net.yozmnews.evaluation.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.domain.tagset.TagSetDefinition;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetTexts;
import net.yozmnews.evaluation.domain.tagset.TagSetWriter;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 첫 기동 적재 — 실제 PostgreSQL 에서 빈 표에만 적재하는지, 다시 불러도 · 동시에 불러도 버전 하나인지, 파일과 내용이 같은 버전이 없을
 * 때만 개정을 알리는지 본다.
 * <p>
 * 픽스처가 체계 행을 모두 지운 뒤 시작하고, 끝에는 다시 적재해 처음 모양으로 둔다.
 * <p>
 * 지키는 규칙: evaluation-R2 · evaluation-R3 · evaluation-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class TagSetSeedingTests {

	private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

	private static final String NOTICE = "기본 체계 파일과 내용이 같은 태그 체계 버전이 없다 — 파일의 개정을 들이려면 체계 변경으로 새 버전을 만든다";

	@Autowired
	TagSetDefinition definition;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	TagSetDirectory directory;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcClient jdbc;

	private TagSetSeeding seeding;

	@BeforeEach
	void startFromEmptyTables() {
		TagSetRows.removeAll(this.jdbc);
		this.seeding = new TagSetSeeding(this.definition, this.tagSets, this.thresholds,
				new TransactionTemplate(this.transactionManager), InstantSource.fixed(NOW));
	}

	@AfterEach
	void endWithTheSeededFirstVersion() {
		TagSetRows.removeAll(this.jdbc);
		this.seeding.seedIfEmpty();
	}

	@Test
	void seedsTheFirstVersionIntoEmptyTables(CapturedOutput output) {
		assertThat(this.seeding.seedIfEmpty()).isTrue();

		assertThat(this.directory.activeTagSet().number()).isEqualTo(1);
		assertThat(TagSetRows.count(this.jdbc, "tag_sets")).isEqualTo(1);
		assertThat(TagSetRows.count(this.jdbc, "tags")).isEqualTo(9 + 48 + 7 + 6);
		assertThat(TagSetRows.count(this.jdbc, "importance_rubrics")).isEqualTo(5);
		assertThat(TagSetRows.count(this.jdbc, "tag_set_thresholds")).isEqualTo(2);
		assertThat(TagSetRows.count(this.jdbc, "tag_set_threshold_changes")).isEqualTo(2);
		assertThat(this.jdbc.sql("select created_by from tag_sets").query(String.class).single()).isEqualTo("SYSTEM");
		assertThat(output).contains("기본 태그 체계를 버전 1로 적재했다");
	}

	@Test
	void failsLoudlyWithoutAnActiveVersion() {
		// 픽스처가 체계 행을 모두 지운 상태 — 첫 기동 적재의 불변식이 깨진 경우(evaluation-R3)
		String message = "활성 태그 체계 버전이 없다";
		assertThatIllegalStateException().isThrownBy(this.directory::activeTagSet).withMessageContaining(message);
		assertThatIllegalStateException().isThrownBy(this.directory::activeThresholds).withMessageContaining(message);
		assertThatIllegalStateException()
			.isThrownBy(() -> this.administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.6"),
					new BigDecimal("0.2"), ChangeActor.OPERATOR))
			.withMessageContaining(message);
		assertThatIllegalStateException().isThrownBy(this.administration::thresholdChanges)
			.withMessageContaining(message);
	}

	@Test
	void saysNothingMoreWhenAVersionHasTheFileContent(CapturedOutput output) {
		this.seeding.seedIfEmpty();

		this.seeding.seedIfEmpty();

		assertThat(output).doesNotContain(NOTICE);
	}

	@Test
	void noticesARevisedFileUntilAVersionWithItsContentExists(CapturedOutput output) {
		this.seeding.seedIfEmpty();
		TagSetDefinition revised = TagSetTexts.parsed(TagSetWriter.write(this.definition.content(),
				this.thresholds.current(this.directory.activeTagSet().id()))
			.replace("description: \"미국\"", "description: \"미국 · 미국령\""));
		TagSetSeeding afterRevision = new TagSetSeeding(revised, this.tagSets, this.thresholds,
				new TransactionTemplate(this.transactionManager), InstantSource.fixed(NOW));

		assertThat(afterRevision.seedIfEmpty()).isFalse();
		assertThat(output).contains(NOTICE);
		assertThat(TagSetRows.count(this.jdbc, "tag_sets")).isEqualTo(1);

		// 개정한 내용을 새 버전으로 들이면 다시 알리지 않는다 — 태그 기준 · 저장 기준은 비교하지 않는다
		TagSetVersions.activate(TagSetWriter.write(revised.content(),
				this.thresholds.current(this.directory.activeTagSet().id())).replace("tagThreshold: 0.5", "tagThreshold: 0.6"),
				this.tagSets, this.thresholds, new TransactionTemplate(this.transactionManager), NOW);
		int noticesBefore = occurrences(output.getAll(), NOTICE);
		afterRevision.seedIfEmpty();

		assertThat(occurrences(output.getAll(), NOTICE)).isEqualTo(noticesBefore);
	}

	@Test
	void doesNothingWhenAVersionExists() {
		this.seeding.seedIfEmpty();

		assertThat(this.seeding.seedIfEmpty()).isFalse();

		assertThat(TagSetRows.count(this.jdbc, "tag_sets")).isEqualTo(1);
		assertThat(TagSetRows.count(this.jdbc, "tag_set_threshold_changes")).isEqualTo(2);
	}

	@Test
	void concurrentSeedingWaitsAndWritesOnlyOneVersion() throws Exception {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		CountDownLatch seeded = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			boolean result = this.seeding.seedIfEmpty();
			seeded.countDown();
			awaitQuietly(commit);
			return result;
		}));
		try {
			assertThat(seeded.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(this.seeding::seedIfEmpty);
			Thread.sleep(500);
			assertThat(second).as("앞 적재가 커밋하기 전에는 뒤 적재가 기다린다").isNotDone();
			commit.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
			assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
		}
		finally {
			commit.countDown();
		}
		assertThat(TagSetRows.count(this.jdbc, "tag_sets")).isEqualTo(1);
		assertThat(TagSetRows.count(this.jdbc, "tag_set_thresholds")).isEqualTo(2);
		assertThat(TagSetRows.count(this.jdbc, "tag_set_threshold_changes")).isEqualTo(2);
	}

	private static int occurrences(String text, String part) {
		int count = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			count++;
		}
		return count;
	}

}
