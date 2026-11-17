package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.domain.interest.CandidateVolume;
import net.yozmnews.user.domain.interest.DigestSchedule;
import net.yozmnews.user.domain.interest.DigestVolume;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestText;
import net.yozmnews.user.domain.interest.ModelRef;
import net.yozmnews.user.domain.interest.ReportSettings;
import net.yozmnews.user.domain.preference.PreferenceAction;
import net.yozmnews.user.domain.preference.PreferenceCause;
import net.yozmnews.user.domain.preference.PreferenceChange;
import net.yozmnews.user.domain.preference.PreferenceSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 관심 설정의 저장 — 설정 행(없을 때만 넣기 · 잠금 읽기 · 판을 올리는 바꾸기 · 설정 행 없는 계정 · 선호 버전이 다른 사용자), 태그 선호의 현재
 * 상태와 이력, 제외 소스, 저장 제약(종류마다 모드 하나 · 고른 모델의 짝). 시험마다 트랜잭션이 되돌려진다.
 * <p>
 * 지키는 규칙: user-R43 · user-R45 · user-R47 · user-R49
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcInterestSettings.class, JdbcTagPreferences.class,
		JdbcExcludedSources.class })
class JdbcInterestStorageTests {

	private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-15T11:00:00Z");

	@Autowired
	JdbcInterestSettings settings;

	@Autowired
	JdbcTagPreferences preferences;

	@Autowired
	JdbcExcludedSources excluded;

	@Autowired
	JdbcClient jdbc;

	private UserId alice;

	private UserId bob;

	private UserId carol;

	@BeforeEach
	void users() {
		this.alice = user("alice@example.com");
		this.bob = user("bob@example.com");
		this.carol = user("carol@example.com");
	}

	@Test
	void aSettingsRowIsInsertedOnlyWhenAbsentAndReadBackUnderALock() {
		assertThat(this.settings.insertIfAbsent(InterestSettings.defaults(this.alice, new TagSetId(3), NOW))).isTrue();
		assertThat(this.settings.insertIfAbsent(InterestSettings.defaults(this.alice, new TagSetId(4), LATER)))
			.isFalse();

		InterestSettings read = this.settings.lockByUser(this.alice).orElseThrow();
		assertThat(read.preferenceVersion()).isEqualTo(new TagSetId(3));
		assertThat(read.schedule()).isEqualTo(new DigestSchedule(ZoneId.of("Asia/Seoul"), LocalTime.of(7, 0)));
		assertThat(read.interestText()).isEqualTo(new InterestText(""));
		assertThat(read.model()).isNull();
		assertThat(read.reports()).isEqualTo(new ReportSettings(true, DayOfWeek.MONDAY, true));
		assertThat(read.volume()).isEqualTo(new DigestVolume(CandidateVolume.NORMAL, null, 3));
		assertThat(read.updatedAt()).isEqualTo(NOW);
		assertThat(read.version()).isZero();
		assertThat(this.settings.lockByUser(this.bob)).isEmpty();
	}

	@Test
	void anUpdateRaisesTheVersionAndAStaleOneFails() {
		this.settings.insertIfAbsent(InterestSettings.defaults(this.alice, new TagSetId(3), NOW));
		InterestSettings read = this.settings.lockByUser(this.alice).orElseThrow();

		this.settings.update(read
			.withDigest(new DigestSchedule(ZoneId.of("America/New_York"), LocalTime.of(6, 30)), new InterestText("배터리"),
					new ReportSettings(false, DayOfWeek.FRIDAY, false), LATER)
			.withModel(new ModelRef(Provider.ANTHROPIC, "claude-haiku-5-5"), LATER)
			.withPreferenceVersion(new TagSetId(5), LATER)
			.withVolume(new DigestVolume(CandidateVolume.CUSTOM, 45, 0), LATER));

		InterestSettings again = this.settings.lockByUser(this.alice).orElseThrow();
		assertThat(again.version()).isOne();
		assertThat(again.preferenceVersion()).isEqualTo(new TagSetId(5));
		assertThat(again.schedule()).isEqualTo(new DigestSchedule(ZoneId.of("America/New_York"), LocalTime.of(6, 30)));
		assertThat(again.interestText()).isEqualTo(new InterestText("배터리"));
		assertThat(again.model()).isEqualTo(new ModelRef(Provider.ANTHROPIC, "claude-haiku-5-5"));
		assertThat(again.reports()).isEqualTo(new ReportSettings(false, DayOfWeek.FRIDAY, false));
		assertThat(again.volume()).isEqualTo(new DigestVolume(CandidateVolume.CUSTOM, 45, 0));
		assertThat(again.updatedAt()).isEqualTo(LATER);
		assertThatIllegalStateException().isThrownBy(() -> this.settings.update(read));
	}

	@Test
	void accountsWithoutSettingsAndUsersOnAnotherVersionArePagedInIdOrder() {
		this.settings.insertIfAbsent(InterestSettings.defaults(this.bob, new TagSetId(3), NOW));

		assertThat(this.settings.withoutSettings(0, 10)).containsExactly(this.alice, this.carol);
		assertThat(this.settings.withoutSettings(this.alice.value(), 10)).containsExactly(this.carol);
		assertThat(this.settings.withoutSettings(0, 1)).containsExactly(this.alice);

		this.settings.insertIfAbsent(InterestSettings.defaults(this.alice, new TagSetId(2), NOW));
		this.settings.insertIfAbsent(InterestSettings.defaults(this.carol, new TagSetId(2), NOW));
		assertThat(this.settings.withPreferenceVersionOtherThan(new TagSetId(3), 0, 10))
			.containsExactly(this.alice, this.carol);
		assertThat(this.settings.withPreferenceVersionOtherThan(new TagSetId(3), this.alice.value(), 10))
			.containsExactly(this.carol);
		assertThat(this.settings.withPreferenceVersionOtherThan(new TagSetId(2), 0, 10)).containsExactly(this.bob);
	}

	@Test
	void appliedChangesUpdateTheCurrentStateAndAppendHistoryRows() {
		this.preferences.apply(this.alice, List.of(new PreferenceChange(TagKind.TOPIC, "politics", PreferenceAction.ADD),
				new PreferenceChange(TagKind.FORMAT, "brief", PreferenceAction.ADD)), PreferenceCause.ONBOARDING,
				new TagSetId(3), NOW);
		this.preferences.apply(this.alice,
				List.of(new PreferenceChange(TagKind.TOPIC, "politics", PreferenceAction.REMOVE),
						new PreferenceChange(TagKind.REGION, "us", PreferenceAction.ADD)),
				PreferenceCause.USER, new TagSetId(4), LATER);

		assertThat(this.preferences.load(this.alice))
			.isEqualTo(new PreferenceSet(Set.of(), Set.of("us"), Set.of("brief")));
		assertThat(this.preferences.load(this.bob)).isEqualTo(PreferenceSet.empty());
		assertThat(this.jdbc.sql("""
				select cause, tag_set_id, tag_kind, tag_key, mode, action from user_tag_preference_events
				where user_id = :user order by id
				""").param("user", this.alice.value()).query().listOfRows())
			.containsExactly(event("ONBOARDING", 3, "TOPIC", "politics", "SUBSCRIBE", "ADD"),
					event("ONBOARDING", 3, "FORMAT", "brief", "EXCLUDE", "ADD"),
					event("USER", 4, "TOPIC", "politics", "SUBSCRIBE", "REMOVE"),
					event("USER", 4, "REGION", "us", "EXCLUDE", "ADD"));
	}

	@Test
	void theStorageRefusesAModeThatDoesNotFitTheKind() {
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'REGION', 'us', 'SUBSCRIBE', now())
				""").param("user", this.alice.value()).update());
	}

	@Test
	void theStorageRefusesAProviderWithoutAModel() {
		this.settings.insertIfAbsent(InterestSettings.defaults(this.bob, new TagSetId(3), NOW));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.jdbc.sql("update user_settings set llm_provider = 'openai' where user_id = :user")
				.param("user", this.bob.value())
				.update());
	}

	@Test
	void excludedSourcesAreReplacedAndForgottenPerSource() {
		this.excluded.replace(this.alice, Set.of(new SourceId(1), new SourceId(2)), NOW);
		this.excluded.replace(this.bob, Set.of(new SourceId(2)), NOW);

		this.excluded.replace(this.alice, Set.of(new SourceId(2), new SourceId(3)), LATER);

		assertThat(this.excluded.load(this.alice)).containsExactlyInAnyOrder(new SourceId(2), new SourceId(3));
		assertThat(this.excluded.forget(new SourceId(2))).isEqualTo(2);
		assertThat(this.excluded.forget(new SourceId(2))).isZero();
		assertThat(this.excluded.load(this.alice)).containsExactly(new SourceId(3));
		assertThat(this.excluded.load(this.bob)).isEmpty();
	}

	@Test
	void deletingAnAccountDeletesItsInterestRows() {
		this.settings.insertIfAbsent(InterestSettings.defaults(this.alice, new TagSetId(3), NOW));
		this.preferences.apply(this.alice, List.of(new PreferenceChange(TagKind.FORMAT, "brief", PreferenceAction.ADD)),
				PreferenceCause.ONBOARDING, new TagSetId(3), NOW);
		this.excluded.replace(this.alice, Set.of(new SourceId(1)), NOW);

		this.jdbc.sql("delete from users where id = :user").param("user", this.alice.value()).update();

		for (String table : List.of("user_settings", "user_tag_preferences", "user_tag_preference_events",
				"user_excluded_sources")) {
			assertThat(this.jdbc.sql("select count(*) from " + table + " where user_id = :user")
				.param("user", this.alice.value())
				.query(Long.class)
				.single()).as(table).isZero();
		}
	}

	private UserId user(String email) {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values (:email, :email, 'USER', 'ACTIVE', now(), 0) returning id
				""").param("email", email).query(Long.class).single());
	}

	private static Map<String, Object> event(String cause, long version, String kind, String key, String mode,
			String action) {
		return Map.of("cause", cause, "tag_set_id", version, "tag_kind", kind, "tag_key", key, "mode", mode, "action",
				action);
	}

}
