package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdWarning;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
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
 * 두 기준 변경과 이력 — 실제 PostgreSQL 에서 이전 값 · 새 값 · 행위자 · 시각, 경고, 바꾸지 않는 결과, 동시 변경의 줄 서기를 본다.
 * <p>
 * 지키는 규칙: evaluation-R11 ~ evaluation-R13
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TagSetAdministrationTests {

	private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	TagSetDirectory directory;

	@Autowired
	JdbcClient jdbc;

	private TagSetAdministrationService administration;

	@BeforeEach
	void startFromTheInitialThresholds() {
		TagSetRows.resetThresholds(this.jdbc);
		this.administration = new TagSetAdministrationService(this.tagSets, this.thresholds,
				new TransactionTemplate(this.transactionManager), InstantSource.fixed(NOW));
	}

	@AfterEach
	void restoreTheInitialThresholds() {
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void changesTheThresholdsAndRecordsTheChange() {
		ThresholdChangeResult result = change(MultiTagKind.TOPIC, "0.6", "0.2");

		assertThat(result).isEqualTo(new ThresholdChangeResult.Changed(thresholds(MultiTagKind.TOPIC, "0.500", "0.100"),
				thresholds(MultiTagKind.TOPIC, "0.600", "0.200"), Set.of()));
		assertThat(this.administration.thresholdChanges().get(0)).isEqualTo(new ThresholdChangeView(MultiTagKind.TOPIC,
				new BigDecimal("0.500"), new BigDecimal("0.100"), new BigDecimal("0.600"), new BigDecimal("0.200"),
				ChangeActor.OPERATOR, NOW));
		assertThat(this.administration.thresholdChanges()).hasSize(3);
	}

	@Test
	void warnsWhenTheStoreThresholdIsLowered() {
		change(MultiTagKind.TOPIC, "0.6", "0.2");

		ThresholdChangeResult result = change(MultiTagKind.TOPIC, "0.6", "0.05");

		assertThat(result).isInstanceOf(ThresholdChangeResult.Changed.class);
		assertThat(((ThresholdChangeResult.Changed) result).warnings())
			.containsExactly(ThresholdWarning.STORE_THRESHOLD_LOWERED);
	}

	@Test
	void writesNothingForTheSameValuesOrValuesOutOfRange() {
		assertThat(change(MultiTagKind.REGION, "0.50", "0.1000"))
			.isEqualTo(new ThresholdChangeResult.Unchanged(thresholds(MultiTagKind.REGION, "0.500", "0.100")));
		assertThat(change(MultiTagKind.REGION, "1.5", "0.1")).isEqualTo(new ThresholdChangeResult.OutOfRange());

		assertThat(this.administration.thresholdChanges()).hasSize(2);
		assertThat(this.directory.activeThresholds().region()).isEqualTo(thresholds(MultiTagKind.REGION, "0.500", "0.100"));
	}

	@Test
	void concurrentChangesQueueSoThePreviousValueIsTheOneJustCommitted() throws Exception {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		CountDownLatch changed = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
			change(MultiTagKind.TOPIC, "0.6", "0.2");
			changed.countDown();
			awaitQuietly(commit);
		}));
		try {
			assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<ThresholdChangeResult> second = CompletableFuture
				.supplyAsync(() -> change(MultiTagKind.TOPIC, "0.7", "0.2"));
			Thread.sleep(500);
			assertThat(second).as("앞 변경이 커밋하기 전에는 뒤 변경이 기다린다").isNotDone();
			commit.countDown();
			first.get(10, TimeUnit.SECONDS);
			assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(new ThresholdChangeResult.Changed(
					thresholds(MultiTagKind.TOPIC, "0.600", "0.200"), thresholds(MultiTagKind.TOPIC, "0.700", "0.200"),
					Set.of()));
		}
		finally {
			commit.countDown();
		}
		List<ThresholdChangeView> changes = this.administration.thresholdChanges();
		assertThat(changes.get(0).previousTagThreshold()).isEqualTo(new BigDecimal("0.600"));
		assertThat(changes.get(0).tagThreshold()).isEqualTo(new BigDecimal("0.700"));
		assertThat(changes.get(1).previousTagThreshold()).isEqualTo(new BigDecimal("0.500"));
		assertThat(changes.get(1).tagThreshold()).isEqualTo(new BigDecimal("0.600"));
	}

	private ThresholdChangeResult change(MultiTagKind kind, String tag, String store) {
		return this.administration.changeThresholds(kind, new BigDecimal(tag), new BigDecimal(store),
				ChangeActor.OPERATOR);
	}

	private static TagThresholds thresholds(MultiTagKind kind, String tag, String store) {
		return new TagThresholds(kind, new BigDecimal(tag), new BigDecimal(store));
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
