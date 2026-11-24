package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.yozmnews.account.application.ProvisionedAccounts;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.domain.account.Onboarding;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.AccountEvents;
import net.yozmnews.user.application.DigestSettings.DigestSettingsOutcome;
import net.yozmnews.user.application.DigestSettings.DigestSettingsView;
import net.yozmnews.user.application.TagPreferences.TagPreferenceSaveOutcome;
import net.yozmnews.user.application.TagPreferences.TagPreferenceView;
import net.yozmnews.user.domain.interest.DigestSchedule;
import net.yozmnews.user.domain.interest.InterestText;
import net.yozmnews.user.domain.interest.ReportSettings;
import net.yozmnews.user.domain.preference.PreferenceSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 태그 선호 조회 · 저장 — 저장은 정규화해 통째로 바꾸고 차이를 이력(원인 USER)으로 남긴다. 화면이 본 버전이 다르면 바꾸지 않는다. 선호 버전이
 * 활성보다 앞선 사용자(활성 전환은 커밋됐고 리스너는 아직 닿지 않음 — 버전 2 를 켠 뒤 그 사용자의 설정 행을 버전 1 상태로 되돌려 만든다)는 조회 ·
 * 저장이 먼저 따라가게 한다 — 같은 앞 단계를 쓰는 일정 저장도 따라간 행 위에서 저장한다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R45 · user-R46 · user-R47
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TagPreferencesTests {

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	@Autowired
	TagPreferences tagPreferences;

	@Autowired
	DigestSettings digestSettings;

	@Autowired
	ApplicationContext context;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	JdbcClient jdbc;

	private SecondTagSet second;

	private TagSetId first;

	@BeforeEach
	void start() {
		this.second = new SecondTagSet(this.administration, this.jdbc);
		this.second.clean();
		this.first = this.tagSets.activeTagSet().id();
	}

	@AfterEach
	void restore() {
		this.second.awaitListener();
		this.second.clean();
	}

	@Test
	void aSaveIsNormalizedAndLeavesUserHistory() {
		UserId user = account("alice@example.com");

		TagPreferenceSaveOutcome outcome = this.tagPreferences.save(user, this.first,
				new PreferenceSet(Set.of("economy", "economy.markets", "politics.executive"), Set.of("us"),
						Set.of("brief")),
				NOW);

		assertThat(outcome).isEqualTo(new TagPreferenceSaveOutcome.Saved(
				new PreferenceSet(Set.of("economy", "politics.executive"), Set.of("us"), Set.of("brief"))));
		assertThat(this.tagPreferences.view(user, NOW).preferences())
			.isEqualTo(new PreferenceSet(Set.of("economy", "politics.executive"), Set.of("us"), Set.of("brief")));
		assertThat(history(user, "USER")).containsExactly(event(this.first, "FORMAT", "press-release", "REMOVE"),
				event(this.first, "TOPIC", "economy", "ADD"), event(this.first, "TOPIC", "politics.executive", "ADD"),
				event(this.first, "REGION", "us", "ADD"));
		// 선호 버전이 활성과 같으면 먼저 따라가기는 아무것도 쓰지 않는다
		assertThat(history(user, "TAG_SET_CHANGE")).isEmpty();
		assertThat(notices(user)).isZero();
	}

	@Test
	void aSaveAgainstAnotherVersionOrWithAnUnknownKeyChangesNothing() {
		UserId user = account("bob@example.com");
		PreferenceSet before = this.tagPreferences.view(user, NOW).preferences();

		assertThat(this.tagPreferences.save(user, new TagSetId(this.first.value() + 1000),
				new PreferenceSet(Set.of("economy"), Set.of(), Set.of()), NOW))
			.isEqualTo(new TagPreferenceSaveOutcome.TagSetChanged(this.first));
		assertThatExceptionOfType(TagPreferences.UnknownTagKeysException.class)
			.isThrownBy(() -> this.tagPreferences.save(user, this.first,
					new PreferenceSet(Set.of("economy", "sports.nowhere"), Set.of(), Set.of()), NOW));

		assertThat(this.tagPreferences.view(user, NOW).preferences()).isEqualTo(before);
		assertThat(history(user, "USER")).isEmpty();
	}

	@Test
	void aViewFollowsAUserTheListenerHasNotReachedYet() {
		UserId user = account("carol@example.com");
		TagSetId active = this.second.activate(this.first);
		this.second.awaitListener();
		behindOn(user, this.first);

		TagPreferenceView view = this.tagPreferences.view(user, NOW);

		assertThat(view.tagSet().id()).isEqualTo(active);
		assertThat(view.preferences())
			.isEqualTo(new PreferenceSet(Set.of(), Set.of(), Set.of("brief", "press-release", "listicle")));
		assertThat(version(user)).isEqualTo(active.value());
		assertThat(history(user, "TAG_SET_CHANGE")).containsExactly(event(this.first, "REGION", "china", "REMOVE"),
				event(active, "FORMAT", "listicle", "ADD"));
		assertThat(notices(user)).isOne();
	}

	@Test
	void aSaveFollowsFirstAndThenRecordsTheUsersOwnChangeOnTheActiveVersion() {
		UserId user = account("dave@example.com");
		TagSetId active = this.second.activate(this.first);
		this.second.awaitListener();
		behindOn(user, this.first);

		TagPreferenceSaveOutcome outcome = this.tagPreferences.save(user, active,
				new PreferenceSet(Set.of("politics"), Set.of("nippon"), Set.of("brief", "press-release", "listicle")),
				NOW);

		assertThat(outcome).isInstanceOf(TagPreferenceSaveOutcome.Saved.class);
		assertThat(history(user, "TAG_SET_CHANGE")).containsExactly(event(this.first, "REGION", "china", "REMOVE"),
				event(active, "FORMAT", "listicle", "ADD"));
		assertThat(history(user, "USER")).containsExactly(event(active, "TOPIC", "politics", "ADD"),
				event(active, "REGION", "nippon", "ADD"));
		assertThat(notices(user)).isOne();
	}

	@Test
	void aStaleSaveStillCommitsTheFollowing() {
		UserId user = account("erin@example.com");
		TagSetId active = this.second.activate(this.first);
		this.second.awaitListener();
		behindOn(user, this.first);

		assertThat(this.tagPreferences.save(user, this.first, new PreferenceSet(Set.of("politics"), Set.of(), Set.of()),
				NOW)).isEqualTo(new TagPreferenceSaveOutcome.TagSetChanged(active));

		assertThat(version(user)).isEqualTo(active.value());
		assertThat(history(user, "USER")).isEmpty();
	}

	@Test
	void aDigestSettingsSaveOnAUserBehindFollowsFirstAndSavesOnTheFollowedRow() {
		UserId user = account("frank@example.com");
		TagSetId active = this.second.activate(this.first);
		this.second.awaitListener();
		behindOn(user, this.first);

		DigestSettingsOutcome outcome = this.digestSettings.save(user, "Europe/Paris", LocalTime.of(6, 30), " 배터리 ",
				new ReportSettings(false, DayOfWeek.FRIDAY, true), NOW);

		assertThat(outcome).isEqualTo(new DigestSettingsOutcome.Saved(
				new DigestSettingsView(new DigestSchedule(ZoneId.of("Europe/Paris"), LocalTime.of(6, 30)),
						new InterestText("배터리"), new ReportSettings(false, DayOfWeek.FRIDAY, true))));
		assertThat(version(user)).isEqualTo(active.value());
		assertThat(notices(user)).isOne();
		// 판 — 만들 때 0 → 리스너의 따라가기 1(그 뒤 버전 1 상태로 되돌림, 판은 그대로) → 먼저 따라가기 2 → 저장 3
		assertThat(this.jdbc.sql("select version from user_settings where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single()).isEqualTo(3L);
	}

	/** 그 사용자를 버전 1 의 상태(기본 제외 둘 · 지역 china 제외)로 되돌린다 — 리스너가 아직 닿지 않은 사용자 */
	private void behindOn(UserId user, TagSetId version) {
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user").param("user", user.value()).update();
		this.jdbc.sql("delete from user_tag_preference_events where user_id = :user").param("user", user.value()).update();
		this.jdbc.sql("delete from user_notices where user_id = :user").param("user", user.value()).update();
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at) values
				  (:user, 'FORMAT', 'brief', 'EXCLUDE', now()), (:user, 'FORMAT', 'press-release', 'EXCLUDE', now()),
				  (:user, 'REGION', 'china', 'EXCLUDE', now())
				""").param("user", user.value()).update();
		this.jdbc.sql("update user_settings set tag_set_id = :version where user_id = :user")
			.param("version", version.value())
			.param("user", user.value())
			.update();
	}

	private UserId account(String email) {
		UserId user = ProvisionedAccounts.provision(this.context, Onboarding.OPEN_SIGN_UP, email, "사용자", "correct horse battery", NOW).orElseThrow();
		AccountEvents.awaitCreatedHandled(this.jdbc);
		return user;
	}

	private long version(UserId user) {
		return this.jdbc.sql("select tag_set_id from user_settings where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

	private List<Map<String, Object>> history(UserId user, String cause) {
		return this.jdbc.sql("""
				select tag_set_id, tag_kind, tag_key, action from user_tag_preference_events
				where user_id = :user and cause = :cause order by id
				""").param("user", user.value()).param("cause", cause).query().listOfRows();
	}

	private static Map<String, Object> event(TagSetId version, String kind, String key, String action) {
		return Map.of("tag_set_id", version.value(), "tag_kind", kind, "tag_key", key, "action", action);
	}

	private long notices(UserId user) {
		return this.jdbc.sql("select count(*) from user_notices where user_id = :user and kind = 'PREFERENCES_ADJUSTED'")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

}
