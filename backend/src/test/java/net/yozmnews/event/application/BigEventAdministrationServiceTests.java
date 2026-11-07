package net.yozmnews.event.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.BigEventCriteriaChange;
import net.yozmnews.event.BigEventCriteriaChangeResult;
import net.yozmnews.event.BigEventCriteriaRows;
import net.yozmnews.event.domain.bigevent.BigEventCriteriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 큰 사건 기준의 처음 값 적재와 운영 API — 빈 표에만 적재하는지, 바꾸기 · 같은 값 · 범위 밖의 결과와 이력, 처음 값이 없을 때의
 * 거부, 동시 변경이 줄을 서는지를 실제 PostgreSQL 에서 본다.
 * <p>
 * 기준은 인스턴스에 한 행이라 다른 시험과 공유된다 — 시작과 끝에 지금 값 · 이력을 비우고, 끝에는 처음 값을 다시 적재한다.
 * <p>
 * 지키는 규칙: event-R21
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BigEventAdministrationServiceTests {

	private static final Instant SEEDED = Instant.parse("2026-11-07T00:00:00Z");

	private static final Instant CHANGED = Instant.parse("2026-11-07T01:00:00Z");

	private static final BigEventCriteria INITIAL = new BigEventCriteria(3, new BigDecimal("3.00"));

	@Autowired
	BigEventCriteriaRepository criteria;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private BigEventCriteriaSeeding seeding;

	@BeforeEach
	void startFromEmptyTables() {
		BigEventCriteriaRows.removeAll(this.jdbc);
		this.seeding = new BigEventCriteriaSeeding(this.criteria, this.transaction, InstantSource.fixed(SEEDED));
	}

	@AfterEach
	void endWithTheSeededFirstValue() {
		BigEventCriteriaRows.removeAll(this.jdbc);
		this.seeding.seedIfAbsent();
	}

	@Test
	void seedsTheFirstValueOnlyIntoAnEmptyTable() {
		assertThat(this.seeding.seedIfAbsent()).isTrue();
		assertThat(this.seeding.seedIfAbsent()).isFalse();

		assertThat(service(this.criteria).criteria()).isEqualTo(INITIAL);
		assertThat(service(this.criteria).changes())
			.containsExactly(new BigEventCriteriaChange(null, INITIAL, ChangeActor.SYSTEM, SEEDED));
	}

	@Test
	void changingRecordsThePreviousValueAndTheOperator() {
		this.seeding.seedIfAbsent();
		BigEventAdministrationService service = service(this.criteria);
		BigEventCriteria changed = new BigEventCriteria(4, new BigDecimal("3.50"));

		assertThat(service.change(4, new BigDecimal("3.5"), ChangeActor.OPERATOR))
			.isEqualTo(new BigEventCriteriaChangeResult.Changed(INITIAL, changed));
		assertThat(service.criteria()).isEqualTo(changed);
		assertThat(service.changes()).containsExactly(
				new BigEventCriteriaChange(INITIAL, changed, ChangeActor.OPERATOR, CHANGED),
				new BigEventCriteriaChange(null, INITIAL, ChangeActor.SYSTEM, SEEDED));
	}

	@Test
	void theSameValueChangesNothing() {
		this.seeding.seedIfAbsent();
		BigEventAdministrationService service = service(this.criteria);

		assertThat(service.change(3, new BigDecimal("3"), ChangeActor.OPERATOR))
			.isEqualTo(new BigEventCriteriaChangeResult.Unchanged(INITIAL));
		assertThat(service.changes()).hasSize(1);
	}

	@Test
	void valuesOutOfRangeChangeNothing() {
		this.seeding.seedIfAbsent();
		BigEventAdministrationService service = service(this.criteria);

		for (BigEventCriteriaChangeResult result : List.of(service.change(0, new BigDecimal("3.0"), ChangeActor.OPERATOR),
				service.change(3, new BigDecimal("0.99"), ChangeActor.OPERATOR),
				service.change(3, new BigDecimal("5.01"), ChangeActor.OPERATOR),
				service.change(3, new BigDecimal("3.001"), ChangeActor.OPERATOR))) {
			assertThat(result).isEqualTo(new BigEventCriteriaChangeResult.OutOfRange());
		}
		assertThat(service.criteria()).isEqualTo(INITIAL);
		assertThat(service.changes()).hasSize(1);
	}

	@Test
	void withoutTheFirstValueReadingAndChangingAreRefused() {
		BigEventAdministrationService service = service(this.criteria);

		assertThatIllegalStateException().isThrownBy(service::criteria);
		assertThatIllegalStateException()
			.isThrownBy(() -> service.change(4, new BigDecimal("3.50"), ChangeActor.OPERATOR));
		assertThat(service.changes()).isEmpty();
	}

	@Test
	void aSecondChangeWaitsForTheFirstAndRecordsItAsThePreviousValue() throws Exception {
		this.seeding.seedIfAbsent();
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		BigEventAdministrationService pausing = service(new Pausing(this.criteria, paused, resume));

		CompletableFuture<BigEventCriteriaChangeResult> first = CompletableFuture
			.supplyAsync(() -> pausing.change(4, new BigDecimal("3.50"), ChangeActor.OPERATOR));
		CompletableFuture<BigEventCriteriaChangeResult> second;
		try {
			assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
			second = CompletableFuture
				.supplyAsync(() -> service(this.criteria).change(5, new BigDecimal("4.00"), ChangeActor.OPERATOR));
			Thread.sleep(500);
			assertThat(second).as("첫 변경이 기준 행을 잡은 동안 둘째 변경이 기다린다").isNotDone();
			resume.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		BigEventCriteria afterFirst = new BigEventCriteria(4, new BigDecimal("3.50"));
		BigEventCriteria afterSecond = new BigEventCriteria(5, new BigDecimal("4.00"));
		assertThat(second.get()).isEqualTo(new BigEventCriteriaChangeResult.Changed(afterFirst, afterSecond));
		assertThat(this.criteria.changes()).extracting(BigEventCriteriaChange::previous)
			.containsExactly(afterFirst, INITIAL, null);
	}

	private BigEventAdministrationService service(BigEventCriteriaRepository repository) {
		return new BigEventAdministrationService(repository, this.transaction, InstantSource.fixed(CHANGED));
	}

	/** 지금 값을 바꾼 뒤(행을 잠근 채) 신호를 보내고 풀릴 때까지 기다린다 — 변경 트랜잭션을 커밋 전에 세운다 */
	private static final class Pausing implements BigEventCriteriaRepository {

		private final BigEventCriteriaRepository delegate;

		private final CountDownLatch paused;

		private final CountDownLatch resume;

		Pausing(BigEventCriteriaRepository delegate, CountDownLatch paused, CountDownLatch resume) {
			this.delegate = delegate;
			this.paused = paused;
			this.resume = resume;
		}

		@Override
		public boolean seedIfAbsent(BigEventCriteria criteria, Instant now) {
			return this.delegate.seedIfAbsent(criteria, now);
		}

		@Override
		public Optional<BigEventCriteria> current() {
			return this.delegate.current();
		}

		@Override
		public BigEventCriteria lockCurrent() {
			return this.delegate.lockCurrent();
		}

		@Override
		public void change(BigEventCriteria previous, BigEventCriteria next, ChangeActor actor, Instant now) {
			this.delegate.change(previous, next, actor, now);
			this.paused.countDown();
			awaitQuietly(this.resume);
		}

		@Override
		public List<BigEventCriteriaChange> changes() {
			return this.delegate.changes();
		}

	}

}
