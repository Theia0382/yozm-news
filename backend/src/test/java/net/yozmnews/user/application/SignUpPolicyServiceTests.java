package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.user.SignUpPolicyChange;
import net.yozmnews.user.SignUpPolicyChangeResult;
import net.yozmnews.user.domain.signup.SignUpPolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입 정책 — 처음 값은 닫힘이고 기동 때 한 번 적재되며(동시에 적재해도 한 행 · 한 이력), 변경은 이력을 남기고 같은 값이면 아무것도 쓰지 않는다.
 * <p>
 * 지키는 규칙: user-R32
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class SignUpPolicyServiceTests {

	private static final Instant T0 = Instant.parse("2026-11-13T09:00:00Z");

	@Autowired
	SignUpPolicyRepository repository;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clear() {
		this.jdbc.sql("delete from user_sign_up_policy_changes").update();
		this.jdbc.sql("delete from user_sign_up_policy").update();
	}

	/** 같은 컨텍스트를 쓰는 다른 시험이 기동 때의 처음 값을 기대하므로 닫힘으로 되돌린다 */
	@AfterEach
	void restore() {
		clear();
		this.transaction.executeWithoutResult(status -> this.repository.seedIfAbsent(T0));
	}

	@Test
	void seedingStartsClosedWithOneSystemRecord() {
		SignUpPolicySeeding seeding = new SignUpPolicySeeding(this.repository, this.transaction, () -> T0);

		assertThat(seeding.seedIfAbsent()).isTrue();
		assertThat(seeding.seedIfAbsent()).isFalse();

		SignUpPolicyService service = new SignUpPolicyService(this.repository, this.transaction, () -> T0);
		assertThat(service.open()).isFalse();
		assertThat(service.changes()).containsExactly(new SignUpPolicyChange(null, false, ChangeActor.SYSTEM, T0));
	}

	@Test
	void seedingAtTheSameTimeLeavesOneRowAndOneRecord() throws Exception {
		SignUpPolicySeeding seeding = new SignUpPolicySeeding(this.repository, this.transaction, () -> T0);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Callable<Boolean>> seeds = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				seeds.add(seeding::seedIfAbsent);
			}
			List<Boolean> seeded = new ArrayList<>();
			for (Future<Boolean> future : pool.invokeAll(seeds)) {
				seeded.add(future.get());
			}
			assertThat(seeded).containsOnlyOnce(true);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(this.jdbc.sql("select count(*) from user_sign_up_policy").query(Integer.class).single()).isOne();
		assertThat(this.jdbc.sql("select count(*) from user_sign_up_policy_changes").query(Integer.class).single())
			.isOne();
	}

	@Test
	void changesAreRecordedNewestFirstAndTheSameValueWritesNothing() {
		new SignUpPolicySeeding(this.repository, this.transaction, () -> T0).seedIfAbsent();
		Instant t1 = T0.plusSeconds(60);
		Instant t2 = T0.plusSeconds(120);

		assertThat(new SignUpPolicyService(this.repository, this.transaction, () -> t1).change(true,
				ChangeActor.OPERATOR))
			.isEqualTo(new SignUpPolicyChangeResult.Changed(false, true));
		SignUpPolicyService later = new SignUpPolicyService(this.repository, this.transaction, () -> t2);
		assertThat(later.change(true, ChangeActor.OPERATOR)).isEqualTo(new SignUpPolicyChangeResult.Unchanged(true));
		assertThat(later.change(false, ChangeActor.OPERATOR))
			.isEqualTo(new SignUpPolicyChangeResult.Changed(true, false));

		assertThat(later.open()).isFalse();
		assertThat(later.changes()).containsExactly(new SignUpPolicyChange(true, false, ChangeActor.OPERATOR, t2),
				new SignUpPolicyChange(false, true, ChangeActor.OPERATOR, t1),
				new SignUpPolicyChange(null, false, ChangeActor.SYSTEM, T0));
	}

}
