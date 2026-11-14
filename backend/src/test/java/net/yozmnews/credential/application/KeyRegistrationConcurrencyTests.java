package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 같은 주인의 키 등록이 여럿 겹칠 때 — 앱 전체를 실제 PostgreSQL 로 띄우고, 같은 사용자 · 공급자의 등록 넷이 검증을 마친 뒤 저장에 함께 들어가게
 * 맞춰(검증 대역의 장벽), 넷 모두 등록되고 행은 하나만 남는지 본다. 행은 커밋되고 시험 뒤에 지운다.
 * <p>
 * 지키는 규칙: credential-R11
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
class KeyRegistrationConcurrencyTests {

	private static final int REGISTRATIONS = 4;

	private static final UserId OWNER = new UserId(990_152);

	@Autowired
	KeyRegistration registration;

	@Autowired
	ScriptedProviderClients provider;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from llm_credentials where user_id = :user").param("user", OWNER.value()).update();
	}

	@Test
	void overlappingRegistrationsOfOneOwnerAllSucceedAndLeaveOneRow() throws Exception {
		CyclicBarrier validated = new CyclicBarrier(REGISTRATIONS);
		List<String> keys = new ArrayList<>();
		for (int i = 1; i <= REGISTRATIONS; i++) {
			String key = "sk-overlap-" + i + "-0123456789abcd";
			keys.add(key);
			// 넷이 모두 검증을 마칠 때까지 기다린다 — 저장(트랜잭션)에 함께 들어간다
			this.provider.before(key, () -> await(validated), new ProbeResult.Reachable());
		}

		ExecutorService pool = Executors.newFixedThreadPool(REGISTRATIONS);
		try {
			List<Future<RegistrationOutcome>> outcomes = new ArrayList<>();
			for (String key : keys) {
				outcomes.add(pool.submit(() -> this.registration.register(OWNER, Provider.OPENAI, SecretValue.of(key))));
			}
			for (Future<RegistrationOutcome> outcome : outcomes) {
				assertThat(outcome.get(30, TimeUnit.SECONDS)).isInstanceOf(RegistrationOutcome.Registered.class);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(this.jdbc.sql("select count(*) from llm_credentials where user_id = :user and provider = 'openai'")
			.param("user", OWNER.value())
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	void registrationsOfDifferentOwnersTogetherKeepARowEach() throws Exception {
		// 대조 — 주인(사용자 × 공급자)이 다르면 같은 장벽에서 함께 저장해도 모두 등록되고 행이 주인마다 하나다. 서로 기다리지 않는지는
		// 이 시험이 재지 않는다(둘 다 끝나는 것만 본다)
		CyclicBarrier validated = new CyclicBarrier(2);
		this.provider.before("sk-owner-a-0123456789abcd", () -> await(validated), new ProbeResult.Reachable());
		this.provider.before("sk-owner-b-0123456789abcd", () -> await(validated), new ProbeResult.Reachable());

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<RegistrationOutcome> a = pool.submit(() -> this.registration.register(OWNER, Provider.OPENAI,
					SecretValue.of("sk-owner-a-0123456789abcd")));
			Future<RegistrationOutcome> b = pool.submit(() -> this.registration.register(OWNER, Provider.ANTHROPIC,
					SecretValue.of("sk-owner-b-0123456789abcd")));
			assertThat(a.get(30, TimeUnit.SECONDS)).isInstanceOf(RegistrationOutcome.Registered.class);
			assertThat(b.get(30, TimeUnit.SECONDS)).isInstanceOf(RegistrationOutcome.Registered.class);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(this.jdbc.sql("select count(*) from llm_credentials where user_id = :user")
			.param("user", OWNER.value())
			.query(Integer.class)
			.single()).isEqualTo(2);
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(30, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			throw new IllegalStateException("검증 장벽을 넘지 못했다", ex);
		}
	}

}
