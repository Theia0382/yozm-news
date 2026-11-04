package net.yozmnews.evaluation.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdChangeResult;
import net.yozmnews.evaluation.ThresholdChangeView;
import net.yozmnews.evaluation.ThresholdCondition;
import net.yozmnews.evaluation.ThresholdKindStatus;
import net.yozmnews.evaluation.ThresholdStatusView;
import net.yozmnews.evaluation.ThresholdWarning;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
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
 * 태그 기준 · 저장 기준 변경과 이력 — 실제 PostgreSQL 에서 이전 값 · 새 값 · 행위자 · 시각, 경고, 바꾸지 않는 결과, 활성 전환과의
 * 줄 서기, 동시 변경의 줄 서기, 끝난 채움이 지난 저장 기준 최댓값을 내리는 것, 결과의 버전, 기준 상태(지난 저장 기준 최댓값 · 어긋난 상태 ·
 * 이력)를 본다.
 * <p>
 * 지키는 규칙: evaluation-R11 ~ evaluation-R13 · evaluation-R36
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TagSetAdministrationTests {

	private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

	/** 처음 값(첫 기동 적재 — 시험 실행 시각)과 NOW 의 변경을 모두 덮는 범위 */
	private static final Instant COVERED_FROM = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant COVERED_TO = Instant.parse("2100-01-01T00:00:00Z");

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

	@Autowired
	TagSetVersioning versioning;

	@Autowired
	TagBackfillRepository backfills;

	@Autowired
	ThresholdPreviews previews;

	private TagSetAdministrationService administration;

	/** 버전 1 의 ID — 첫 기동 적재가 쓴 행 */
	private TagSetId first;

	@BeforeEach
	void startFromTheInitialThresholds() {
		TagBackfillRows.removeAll(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
		this.first = new TagSetId(
				this.jdbc.sql("select id from tag_sets where number = 1").query(Long.class).single());
		this.administration = new TagSetAdministrationService(this.tagSets, this.thresholds,
				new TransactionTemplate(this.transactionManager), InstantSource.fixed(NOW),
				this.versioning, this.backfills, this.previews);
	}

	@AfterEach
	void restoreTheInitialThresholds() {
		TagBackfillRows.removeAll(this.jdbc);
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

	@Test
	void changesTheThresholdsAndRecordsTheChange() {
		ThresholdChangeResult result = change(MultiTagKind.TOPIC, "0.6", "0.2");

		assertThat(result).isEqualTo(new ThresholdChangeResult.Changed(this.first,
				thresholds(MultiTagKind.TOPIC, "0.500", "0.100"), thresholds(MultiTagKind.TOPIC, "0.600", "0.200"),
				Set.of()));
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
	void aFinishedBackfillOfTheWholeVersionLiftsTheWarningAboutThePastStoreThreshold() {
		change(MultiTagKind.TOPIC, "0.5", "0.3");
		change(MultiTagKind.TOPIC, "0.5", "0.1");
		TagBackfillRows.insertFinished(this.jdbc, this.directory.activeTagSet().id(), COVERED_FROM, COVERED_TO, "0.1",
				0);

		assertThat(change(MultiTagKind.TOPIC, "0.2", "0.1")).isEqualTo(new ThresholdChangeResult.Changed(this.first,
				thresholds(MultiTagKind.TOPIC, "0.500", "0.100"), thresholds(MultiTagKind.TOPIC, "0.200", "0.100"),
				Set.of()));
	}

	@Test
	void withoutABackfillTheTagThresholdBelowThePastStoreThresholdIsWarned() {
		// 대조 — 덮음이 없으면 0.3 으로 저장된 평가가 남아 있다
		change(MultiTagKind.TOPIC, "0.5", "0.3");
		change(MultiTagKind.TOPIC, "0.5", "0.1");

		assertThat(change(MultiTagKind.TOPIC, "0.2", "0.1")).isEqualTo(new ThresholdChangeResult.Changed(this.first,
				thresholds(MultiTagKind.TOPIC, "0.500", "0.100"), thresholds(MultiTagKind.TOPIC, "0.200", "0.100"),
				Set.of(ThresholdWarning.TAG_BELOW_PAST_STORE)));
	}

	@Test
	void aBackfillThatSkippedEvaluationsOfTheVersionDoesNotCover() {
		change(MultiTagKind.TOPIC, "0.5", "0.3");
		change(MultiTagKind.TOPIC, "0.5", "0.1");
		TagBackfillRows.insertFinished(this.jdbc, this.directory.activeTagSet().id(), COVERED_FROM, COVERED_TO, "0.1",
				1);

		assertThat(((ThresholdChangeResult.Changed) change(MultiTagKind.TOPIC, "0.2", "0.1")).warnings())
			.containsExactly(ThresholdWarning.TAG_BELOW_PAST_STORE);
	}

	@Test
	void writesNothingForTheSameValuesOrValuesOutOfRange() {
		assertThat(change(MultiTagKind.REGION, "0.50", "0.1000"))
			.isEqualTo(new ThresholdChangeResult.Unchanged(this.first,
					thresholds(MultiTagKind.REGION, "0.500", "0.100")));
		assertThat(change(MultiTagKind.REGION, "1.5", "0.1")).isEqualTo(new ThresholdChangeResult.OutOfRange());

		assertThat(this.administration.thresholdChanges()).hasSize(2);
		assertThat(this.directory.activeThresholds().region()).isEqualTo(thresholds(MultiTagKind.REGION, "0.500", "0.100"));
	}

	@Test
	void aChangeWaitsWhileAnActivationHoldsTheTagSetTable() throws Exception {
		CompletableFuture<ThresholdChangeResult> change = whileAnotherTransactionHolds("share row exclusive", () -> {
			CompletableFuture<ThresholdChangeResult> started = CompletableFuture
				.supplyAsync(() -> this.administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.6"),
						new BigDecimal("0.1"), ChangeActor.OPERATOR));
			sleepQuietly(300);
			// 활성 전환의 잠금을 쥔 동안 끝나지 않는다 — 활성 버전을 읽기 전에 표 잠금에서 기다린다
			assertThat(started.isDone()).isFalse();
			return started;
		});

		assertThat(change.get(10, TimeUnit.SECONDS)).isInstanceOf(ThresholdChangeResult.Changed.class);
	}

	@Test
	void aChangeDoesNotWaitForAnotherChangeOrAResultWrite() throws Exception {
		// 결과 쓰기 · 다른 변경이 쥐는 잠금(SHARE)과는 충돌하지 않는다 — 잠금을 쥔 동안 끝난다
		ThresholdChangeResult result = whileAnotherTransactionHolds("share",
				() -> this.administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.6"),
						new BigDecimal("0.1"), ChangeActor.OPERATOR));

		assertThat(result).isInstanceOf(ThresholdChangeResult.Changed.class);
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
			assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(new ThresholdChangeResult.Changed(this.first,
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

	@Test
	void theResultsCarryTheVersionThatWasActiveWhenTheChangeWasJudged() {
		// 대조: 활성화 전의 변경은 버전 1 이다
		assertThat(((ThresholdChangeResult.Changed) change(MultiTagKind.TOPIC, "0.6", "0.1")).version())
			.isEqualTo(this.first);
		TagSetId second = TagSetVersions.activate(this.administration.versionText(this.first).orElseThrow(),
				this.tagSets, this.thresholds, new TransactionTemplate(this.transactionManager), NOW);

		assertThat(second).isNotEqualTo(this.first);
		assertThat(((ThresholdChangeResult.Changed) change(MultiTagKind.TOPIC, "0.7", "0.1")).version())
			.isEqualTo(second);
		// 새 버전의 처음 값은 원문의 값(주제 0.6 / 0.1, 지역 0.5 / 0.1)이다 — 같은 값은 바꾸지 않았다는 결과도 그 버전을 싣는다
		assertThat(change(MultiTagKind.REGION, "0.5", "0.1")).isEqualTo(new ThresholdChangeResult.Unchanged(second,
				thresholds(MultiTagKind.REGION, "0.500", "0.100")));
	}

	@Test
	void theStatusGivesTheCurrentValuesThePastMaximumTheStandingConditionsAndTheHistory() {
		change(MultiTagKind.TOPIC, "0.6", "0.2");
		change(MultiTagKind.TOPIC, "0.15", "0.1");
		change(MultiTagKind.REGION, "0.05", "0.1");

		ThresholdStatusView status = this.administration.thresholdStatus();

		assertThat(status.version()).isEqualTo(this.first);
		assertThat(status.versionNumber()).isEqualTo(1);
		// 주제 — 저장 기준을 0.2 로 올렸다가 내렸다. 태그 기준 0.15 는 지난 최댓값 0.2 아래다
		assertThat(status.topic()).isEqualTo(new ThresholdKindStatus(thresholds(MultiTagKind.TOPIC, "0.150", "0.100"),
				new BigDecimal("0.200"),
				Set.of(ThresholdCondition.TAG_BELOW_PAST_STORE, ThresholdCondition.STORE_THRESHOLD_CHANGED)));
		// 지역 — 저장 기준은 바뀐 적이 없고, 태그 기준이 저장 기준 아래다
		assertThat(status.region()).isEqualTo(new ThresholdKindStatus(
				thresholds(MultiTagKind.REGION, "0.050", "0.100"), new BigDecimal("0.100"),
				Set.of(ThresholdCondition.TAG_BELOW_STORE)));
		// 이력 — 처음 값 둘과 변경 셋, 최근 것부터
		assertThat(status.changes()).hasSize(5);
		assertThat(status.changes())
			.extracting(ThresholdChangeView::kind, ThresholdChangeView::tagThreshold, ThresholdChangeView::storeThreshold)
			.startsWith(tuple(MultiTagKind.REGION, new BigDecimal("0.050"), new BigDecimal("0.100")),
					tuple(MultiTagKind.TOPIC, new BigDecimal("0.150"), new BigDecimal("0.100")),
					tuple(MultiTagKind.TOPIC, new BigDecimal("0.600"), new BigDecimal("0.200")));
	}

	@Test
	void theStatusOfTheInitialThresholdsHasNoCondition() {
		ThresholdStatusView status = this.administration.thresholdStatus();

		assertThat(status.topic().conditions()).isEmpty();
		assertThat(status.region().conditions()).isEmpty();
		assertThat(status.topic().pastMaxStoreThreshold()).isEqualTo(new BigDecimal("0.100"));
		assertThat(status.changes()).hasSize(2);
	}

	@Test
	void aFinishedBackfillOfTheWholeVersionLiftsTheMixedStoreFloorsOfTheStatus() {
		change(MultiTagKind.TOPIC, "0.5", "0.2");
		change(MultiTagKind.TOPIC, "0.5", "0.1");
		TagBackfillRows.insertFinished(this.jdbc, this.first, COVERED_FROM, COVERED_TO, "0.1", 0);

		assertThat(this.administration.thresholdStatus().topic().conditions()).isEmpty();
	}

	@Test
	void withoutABackfillTheLoweredStoreThresholdLeavesTheStatusMixed() {
		// 대조 — 덮음이 없으면 0.2 로 저장된 평가가 남는다(태그 기준 0.5 는 지난 최댓값 0.2 보다 높아 그 상태는 없다)
		change(MultiTagKind.TOPIC, "0.5", "0.2");
		change(MultiTagKind.TOPIC, "0.5", "0.1");

		assertThat(this.administration.thresholdStatus().topic().conditions())
			.containsExactly(ThresholdCondition.STORE_THRESHOLD_CHANGED);
	}

	@Test
	void aFinishedBackfillOfTheWholeVersionLowersThePastMaximumOfTheStatus() {
		change(MultiTagKind.TOPIC, "0.5", "0.2");
		change(MultiTagKind.TOPIC, "0.5", "0.1");
		TagBackfillRows.insertFinished(this.jdbc, this.first, COVERED_FROM, COVERED_TO, "0.1", 0);

		assertThat(this.administration.thresholdStatus().topic().pastMaxStoreThreshold())
			.isEqualTo(new BigDecimal("0.100"));
	}

	@Test
	void withoutABackfillThePastMaximumOfTheStatusIsTheLargestStoreThresholdOfTheHistory() {
		// 대조 — 덮음이 없으면 0.2 로 저장된 평가가 남아 있다
		change(MultiTagKind.TOPIC, "0.5", "0.2");
		change(MultiTagKind.TOPIC, "0.5", "0.1");

		assertThat(this.administration.thresholdStatus().topic().pastMaxStoreThreshold())
			.isEqualTo(new BigDecimal("0.200"));
	}

	private ThresholdChangeResult change(MultiTagKind kind, String tag, String store) {
		return this.administration.changeThresholds(kind, new BigDecimal(tag), new BigDecimal(store),
				ChangeActor.OPERATOR);
	}

	private static TagThresholds thresholds(MultiTagKind kind, String tag, String store) {
		return new TagThresholds(kind, new BigDecimal(tag), new BigDecimal(store));
	}

	/** 다른 트랜잭션이 tag_sets 표를 그 모드로 잠근 동안 일을 하고, 일이 끝나면 잠금을 푼다 */
	private <T> T whileAnotherTransactionHolds(String mode, java.util.function.Supplier<T> work) throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
			this.jdbc.sql("lock table tag_sets in " + mode + " mode").update();
			locked.countDown();
			awaitQuietly(release);
		}));
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			return work.get();
		}
		finally {
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
		}
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
