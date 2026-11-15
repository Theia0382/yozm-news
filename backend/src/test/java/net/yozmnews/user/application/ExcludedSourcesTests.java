package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceDeleted;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceName;
import net.yozmnews.user.application.ExcludedSources.ExcludedSourcesView;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 제외 소스 — 고를 소스(숨기지 않은 소스) 안에서만 바꾸고, 숨긴 소스의 제외는 남으며 고를 소스 밖의 요청은 버린다. 지운 소스는 리스너가 그 제외
 * 행을 지운다(다시 처리해도 같다). 소스는 기록된 출처 이름만 가진 행으로 직접 만든다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R49
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ExcludedSourcesTests {

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	@Autowired
	ExcludedSources excludedSources;

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<Long> sources = new ArrayList<>();

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		for (Long source : this.sources) {
			this.jdbc.sql("delete from source_attributions where source_id = :source").param("source", source).update();
			this.jdbc.sql("delete from sources where id = :source").param("source", source).update();
		}
		this.sources.clear();
	}

	@Test
	void aSaveChangesOnlyWithinTheVisibleSourcesAndKeepsAHiddenSourcesExclusion() {
		UserId user = account("alice@example.com");
		SourceId visible = source("보이는일보");
		SourceId other = source("다른일보");
		SourceId hidden = source("숨긴일보");
		this.excludedSources.save(user, Set.of(visible, hidden), NOW);
		this.jdbc.sql("update sources set hidden_at = now() where id = :source").param("source", hidden.value()).update();

		Set<SourceId> saved = this.excludedSources.save(user, Set.of(other, hidden, new SourceId(Long.MAX_VALUE)), NOW);

		assertThat(saved).containsExactly(other);
		assertThat(stored(user)).containsExactlyInAnyOrder(other.value(), hidden.value());
		ExcludedSourcesView view = this.excludedSources.view(user, NOW);
		assertThat(view.sources()).contains(new SourceName(visible, "보이는일보"), new SourceName(other, "다른일보"))
			.doesNotContain(new SourceName(hidden, "숨긴일보"));
		assertThat(view.excluded()).containsExactly(other);
	}

	@Test
	void aDeletedSourcesExclusionsAreForgottenByTheListenerAndAgainChangesNothing() {
		UserId alice = account("alice@example.com");
		UserId bob = account("bob@example.com");
		SourceId gone = source("지운일보");
		SourceId kept = source("남은일보");
		this.excludedSources.save(alice, Set.of(gone, kept), NOW);
		this.excludedSources.save(bob, Set.of(gone), NOW);

		this.transaction.executeWithoutResult(status -> this.events.publishEvent(new SourceDeleted(gone)));
		awaitNoExclusionsOf(gone);

		assertThat(stored(alice)).containsExactly(kept.value());
		assertThat(stored(bob)).isEmpty();
		assertThat(this.excludedSources.forget(gone)).isZero();
	}

	private SourceId source(String name) {
		long id = this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values (:key, false, 0, now()) returning id")
			.param("key", "excluded-sources-" + (this.sources.size() + 1))
			.query(Long.class)
			.single();
		this.jdbc.sql("insert into source_attributions (source_id, name, recorded_at) values (:source, :name, now())")
			.param("source", id)
			.param("name", name)
			.update();
		this.sources.add(id);
		return new SourceId(id);
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return ((ProvisionResult.Created) result).user();
	}

	private List<Long> stored(UserId user) {
		return this.jdbc.sql("select source_id from user_excluded_sources where user_id = :user order by source_id")
			.param("user", user.value())
			.query(Long.class)
			.list();
	}

	private void awaitNoExclusionsOf(SourceId source) {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < deadline) {
			Long left = this.jdbc.sql("select count(*) from user_excluded_sources where source_id = :source")
				.param("source", source.value())
				.query(Long.class)
				.single();
			if (left == 0) {
				return;
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		throw new AssertionError("지운 소스의 제외 행이 남았다: " + source.value());
	}

}
