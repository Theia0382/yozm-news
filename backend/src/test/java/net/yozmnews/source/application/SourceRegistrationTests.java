package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.domain.profile.PresetKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 소스 등록과 기동 대조 — 실제 PostgreSQL 에서 합성 프리셋 multi(피드 one · two)로 본다.
 * <p>
 * 지키는 규칙: source-R1 · source-R2 · source-R3 · source-R8
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SourceRegistrationTests {

	private static final String OPERATOR_TEXT = """
			# 이유: 합성 운영자 프로필
			source:
			  name: 운영자일보
			  homepage: https://operator.example
			  language: ko
			feeds:
			  - key: main
			    url: https://operator.example/rss.xml
			    section: 전체
			""";

	@Autowired
	SourceRegistration registration;

	@Autowired
	PresetFeedReconciliation reconciliation;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ConfigurableApplicationContext context;

	RegistryRows rows;

	@BeforeEach
	void deleteRows() {
		this.rows = new RegistryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void registersAPresetWithEveryFeedDisabled() {
		long source = registered(this.registration.registerPreset(new PresetKey("multi")));

		assertThat(this.rows.feeds(source)).containsExactly("one:false", "two:false");
		assertThat(this.rows.count("feed_poll_states")).isEqualTo(2);
		assertThat(this.jdbc.sql("select preset_key || '/' || enabled from sources where id = :id")
			.param("id", source)
			.query(String.class)
			.single()).isEqualTo("multi/false");
	}

	@Test
	void refusesASecondSourceForTheSamePreset() {
		this.registration.registerPreset(new PresetKey("multi"));

		Registration again = this.registration.registerPreset(new PresetKey("multi"));

		assertThat(again).isEqualTo(new Registration.PresetAlreadyRegistered(new PresetKey("multi")));
		assertThat(this.rows.count("sources")).isEqualTo(1);
		assertThat(this.rows.count("feeds")).isEqualTo(2);
	}

	@Test
	void refusesAPresetMissingFromTheCatalog() {
		assertThat(this.registration.registerPreset(new PresetKey("gone")))
			.isEqualTo(new Registration.PresetNotFound(new PresetKey("gone")));
		assertThat(this.rows.count("sources")).isZero();
	}

	@Test
	void refusesAnInvalidOperatorProfileWithItsProblems() {
		Registration result = this.registration.registerOperatorProfile("source:\n  name: 이름만\n");

		assertThat(result).isInstanceOfSatisfying(Registration.InvalidProfile.class,
				invalid -> assertThat(invalid.problems()).isNotEmpty());
		assertThat(this.rows.count("sources")).isZero();
	}

	@Test
	void keepsTheOperatorProfileTextVerbatim() {
		long source = registered(this.registration.registerOperatorProfile(OPERATOR_TEXT));

		assertThat(this.jdbc.sql("select profile from sources where id = :id")
			.param("id", source)
			.query(String.class)
			.single()).isEqualTo(OPERATOR_TEXT);
		assertThat(this.rows.feeds(source)).containsExactly("main:false");
	}

	@Test
	void reconciliationAddsAFeedNewToThePresetAndWarnsAboutADroppedOne(CapturedOutput output) {
		long source = registered(this.registration.registerPreset(new PresetKey("multi")));
		long two = this.rows.feedId(source, "two");
		this.jdbc.sql("delete from feed_poll_states where feed_id = :feed").param("feed", two).update();
		this.jdbc.sql("delete from feeds where id = :feed").param("feed", two).update();
		this.rows.insertFeed(source, "gone");

		this.context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], this.context,
				Duration.ZERO));

		assertThat(this.rows.feeds(source)).containsExactly("gone:false", "one:false", "two:false");

		this.reconciliation.reconcile();

		assertThat(this.rows.feeds(source)).containsExactly("gone:false", "one:false", "two:false");
		assertThat(this.rows.hasPollState(this.rows.feedId(source, "two"))).isTrue();
		assertThat(output).contains("프로필에서 빠진 피드 — 소스 " + source + " 피드 gone");
	}

	@Test
	void reconciliationWarnsAboutAPresetThatIsGone(CapturedOutput output) {
		this.jdbc.sql("insert into sources (preset_key, enabled, version, created_at) values ('vanished', true, 0, now())")
			.update();

		this.reconciliation.reconcile();

		assertThat(output).contains("프리셋이 사라졌다").contains("vanished");
		assertThat(this.rows.count("feeds")).isZero();
	}

	private static long registered(Registration result) {
		assertThat(result).isInstanceOf(Registration.Registered.class);
		return ((Registration.Registered) result).id().value();
	}

}
