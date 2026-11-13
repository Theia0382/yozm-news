package net.yozmnews.storyline.application;

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
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.storyline.BigStorylineCriteriaChangeResult;
import net.yozmnews.storyline.BigStorylineCriteriaRows;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import net.yozmnews.platform.ChangeActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 큰 판정 기준의 처음 값 적재와 운영 API — 빈 표에만 적재하는지, 바꾸기 · 같은 값 · 범위 밖의 결과와 이력, 처음 값이 없을 때의
 * 거부, 동시 변경이 줄을 서는지를 실제 PostgreSQL 에서 본다.
 * <p>
 * 기준은 인스턴스에 한 행이라 다른 시험과 공유된다 — 시작과 끝에 지금 값 · 이력을 비우고, 끝에는 처음 값을 다시 적재한다.
 * <p>
 * 지키는 규칙: storyline-R20
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BigStorylineAdministrationServiceTests {

	private static final Instant SEEDED = Instant.parse("2026-11-07T00:00:00Z");

	private static final Instant CHANGED = Instant.parse("2026-11-07T01:00:00Z");

	private static final BigStorylineCriteria INITIAL = new BigStorylineCriteria(new BigDecimal("15.00"));

	@Autowired
	BigStorylineCriteriaRepository criteria;

	@Autowired
	StorylineDirectoryService directory;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private BigStorylineCriteriaSeeding seeding;

	@BeforeEach
	void startFromEmptyTables() {
		BigStorylineCriteriaRows.removeAll(this.jdbc);
		this.seeding = new BigStorylineCriteriaSeeding(this.criteria, this.transaction, InstantSource.fixed(SEEDED));
	}

	@AfterEach
	void endWithTheSeededFirstValue() {
		BigStorylineCriteriaRows.removeAll(this.jdbc);
		this.seeding.seedIfAbsent();
	}

	@Test
	void seedsTheFirstValueOnlyIntoAnEmptyTable() {
		assertThat(this.seeding.seedIfAbsent()).isTrue();
		assertThat(this.seeding.seedIfAbsent()).isFalse();

		assertThat(service(this.criteria).criteria()).isEqualTo(INITIAL);
		assertThat(service(this.criteria).changes())
			.containsExactly(new BigStorylineCriteriaChange(null, INITIAL, ChangeActor.SYSTEM, SEEDED));
	}

	@Test
	void changingRecordsThePreviousValueAndTheOperator() {
		this.seeding.seedIfAbsent();
		BigStorylineAdministrationService service = service(this.criteria);
		BigStorylineCriteria changed = new BigStorylineCriteria(new BigDecimal("20.00"));

		assertThat(service.change(new BigDecimal("20"), ChangeActor.OPERATOR))
			.isEqualTo(new BigStorylineCriteriaChangeResult.Changed(INITIAL, changed));
		assertThat(service.criteria()).isEqualTo(changed);
		assertThat(service.changes()).containsExactly(
				new BigStorylineCriteriaChange(INITIAL, changed, ChangeActor.OPERATOR, CHANGED),
				new BigStorylineCriteriaChange(null, INITIAL, ChangeActor.SYSTEM, SEEDED));
	}

	@Test
	void theSameValueChangesNothing() {
		this.seeding.seedIfAbsent();
		BigStorylineAdministrationService service = service(this.criteria);

		assertThat(service.change(new BigDecimal("15"), ChangeActor.OPERATOR))
			.isEqualTo(new BigStorylineCriteriaChangeResult.Unchanged(INITIAL));
		assertThat(service.changes()).hasSize(1);
	}

	@Test
	void valuesOutOfRangeChangeNothing() {
		this.seeding.seedIfAbsent();
		BigStorylineAdministrationService service = service(this.criteria);

		for (BigStorylineCriteriaChangeResult result : List.of(service.change(new BigDecimal("0"), ChangeActor.OPERATOR),
				service.change(new BigDecimal("-0.01"), ChangeActor.OPERATOR),
				service.change(new BigDecimal("10000"), ChangeActor.OPERATOR),
				service.change(new BigDecimal("15.001"), ChangeActor.OPERATOR))) {
			assertThat(result).isEqualTo(new BigStorylineCriteriaChangeResult.OutOfRange());
		}
		assertThat(service.criteria()).isEqualTo(INITIAL);
		assertThat(service.changes()).hasSize(1);
	}

	@Test
	void withoutTheFirstValueReadingAndChangingAreRefused() {
		BigStorylineAdministrationService service = service(this.criteria);

		assertThatIllegalStateException().isThrownBy(service::criteria);
		assertThatIllegalStateException()
			.isThrownBy(() -> service.change(new BigDecimal("20.00"), ChangeActor.OPERATOR));
		assertThat(service.changes()).isEmpty();
	}

	@Test
	void aSecondChangeWaitsForTheFirstAndRecordsItAsThePreviousValue() throws Exception {
		this.seeding.seedIfAbsent();
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		BigStorylineAdministrationService pausing = service(new Pausing(this.criteria, paused, resume));

		CompletableFuture<BigStorylineCriteriaChangeResult> first = CompletableFuture
			.supplyAsync(() -> pausing.change(new BigDecimal("20.00"), ChangeActor.OPERATOR));
		CompletableFuture<BigStorylineCriteriaChangeResult> second;
		try {
			assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
			second = CompletableFuture
				.supplyAsync(() -> service(this.criteria).change(new BigDecimal("25.00"), ChangeActor.OPERATOR));
			Thread.sleep(500);
			assertThat(second).as("첫 변경이 기준 행을 잡은 동안 둘째 변경이 기다린다").isNotDone();
			resume.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		BigStorylineCriteria afterFirst = new BigStorylineCriteria(new BigDecimal("20.00"));
		BigStorylineCriteria afterSecond = new BigStorylineCriteria(new BigDecimal("25.00"));
		assertThat(second.get()).isEqualTo(new BigStorylineCriteriaChangeResult.Changed(afterFirst, afterSecond));
		assertThat(this.criteria.changes()).extracting(BigStorylineCriteriaChange::previous)
			.containsExactly(afterFirst, INITIAL, null);
	}

	private BigStorylineAdministrationService service(BigStorylineCriteriaRepository repository) {
		return new BigStorylineAdministrationService(repository, this.directory, this.transaction, InstantSource.fixed(CHANGED));
	}

	/** 지금 값을 바꾼 뒤(행을 잠근 채) 신호를 보내고 풀릴 때까지 기다린다 — 변경 트랜잭션을 커밋 전에 세운다 */
	private static final class Pausing implements BigStorylineCriteriaRepository {

		private final BigStorylineCriteriaRepository delegate;

		private final CountDownLatch paused;

		private final CountDownLatch resume;

		Pausing(BigStorylineCriteriaRepository delegate, CountDownLatch paused, CountDownLatch resume) {
			this.delegate = delegate;
			this.paused = paused;
			this.resume = resume;
		}

		@Override
		public boolean seedIfAbsent(BigStorylineCriteria criteria, Instant now) {
			return this.delegate.seedIfAbsent(criteria, now);
		}

		@Override
		public Optional<BigStorylineCriteria> current() {
			return this.delegate.current();
		}

		@Override
		public BigStorylineCriteria lockCurrent() {
			return this.delegate.lockCurrent();
		}

		@Override
		public void change(BigStorylineCriteria previous, BigStorylineCriteria next, ChangeActor actor, Instant now) {
			this.delegate.change(previous, next, actor, now);
			this.paused.countDown();
			awaitQuietly(this.resume);
		}

		@Override
		public List<BigStorylineCriteriaChange> changes() {
			return this.delegate.changes();
		}

	}

}
