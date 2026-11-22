package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterests;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.TopicSubscription;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
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
 * 다이제스트가 읽는 관심 설정 — 활성 버전의 사용자는 저장 값을 펼쳐 내고, 선호 버전이 옛 버전인 사용자는 따라가기 결과를 계산해 내되 저장 행 · 이력 ·
 * 알림을 쓰지 않는다. 설정 행이 없으면 계정을 만들 때의 기본값을 계산해 내고 행을 만들지 않는다. 계정이 없으면 빈 값. 실제 PostgreSQL 과 기본 체계(버전
 * 1)에서 시작하고, 버전 2 는 {@link SecondTagSet}(지역 japan → nippon, china 지움, 형식 listicle 기본 제외로 들임)이다.
 * <p>
 * 기대 키는 기본 체계 파일에서 손으로 옮겨 적은 것이다 — 따라가기 · 펼치기 규칙을 시험 안에서 불러 기대값을 만들지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestInterestsDirectoryTests {

	private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

	/** 기본 체계의 정치 중분류 — default.yaml 순서와 무관하게 집합으로 */
	private static final Set<String> POLITICS = Set.of("politics.executive", "politics.legislature",
			"politics.north-korea", "politics.diplomacy-defense");

	@Autowired
	DigestInterestsDirectory directory;

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private TagSetId first;

	private SecondTagSet second;

	@BeforeEach
	void startFromTheFirstVersion() {
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
	void aUserOnTheActiveVersionGetsTheStoredSettingsWithCategoriesExpanded() {
		UserId alice = account("alice@example.com");
		preference(alice, "TOPIC", "politics", "SUBSCRIBE");
		preference(alice, "TOPIC", "economy.markets", "SUBSCRIBE");
		preference(alice, "REGION", "us", "EXCLUDE");
		this.jdbc.sql("insert into user_excluded_sources (user_id, source_id, created_at) values (:user, 7, now())")
			.param("user", alice.value())
			.update();
		this.jdbc.sql("""
				update user_settings set interest_text = '반도체 공급망', timezone = 'America/New_York',
				  candidate_volume = 'CUSTOM', candidate_count = 45, big_slots = 0, summary_length = 'SHORT',
				  reasoning_effort = 'HIGH'
				where user_id = :user
				""").param("user", alice.value()).update();

		DigestInterests interests = this.directory.interestsOf(alice).orElseThrow();

		assertThat(interests).isEqualTo(new DigestInterests(this.first,
				List.of(new TopicSubscription("economy.markets", Set.of("economy.markets")),
						new TopicSubscription("politics", POLITICS)),
				Set.of("us"), Set.of("brief", "press-release"), Set.of(new SourceId(7)), "반도체 공급망",
				ZoneId.of("America/New_York"), 45, 0, SummaryLength.SHORT, ReasoningEffort.HIGH));
	}

	@Test
	void aUserLeftOnTheOldVersionGetsTheFollowedPreferencesAndNothingIsWritten() {
		TagSetId secondVersion = this.second.activate(this.first);
		this.second.awaitListener();
		UserId bob = account("bob@example.com");
		// 리스너가 닿기 전의 틈 — 선호 버전 1 과 그 버전의 키
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user").param("user", bob.value()).update();
		preference(bob, "REGION", "japan", "EXCLUDE");
		preference(bob, "REGION", "china", "EXCLUDE");
		preference(bob, "TOPIC", "politics", "SUBSCRIBE");
		preference(bob, "FORMAT", "brief", "EXCLUDE");
		preference(bob, "FORMAT", "press-release", "EXCLUDE");
		this.jdbc.sql("update user_settings set tag_set_id = :first where user_id = :user")
			.param("first", this.first.value())
			.param("user", bob.value())
			.update();
		List<Map<String, Object>> rowsBefore = preferences(bob);
		long historyBefore = count("user_tag_preference_events", bob);
		long noticesBefore = count("user_notices", bob);

		DigestInterests interests = this.directory.interestsOf(bob).orElseThrow();

		assertThat(interests).isEqualTo(new DigestInterests(secondVersion,
				List.of(new TopicSubscription("politics", POLITICS)), Set.of("nippon"),
				Set.of("brief", "listicle", "press-release"), Set.of(), "", ZoneId.of("Asia/Seoul"), 40, 3,
				SummaryLength.NORMAL, ReasoningEffort.MEDIUM));
		assertThat(this.jdbc.sql("select tag_set_id from user_settings where user_id = :user")
			.param("user", bob.value())
			.query(Long.class)
			.single()).as("선호 버전은 그대로").isEqualTo(this.first.value());
		assertThat(preferences(bob)).as("선호 행은 그대로").isEqualTo(rowsBefore);
		assertThat(count("user_tag_preference_events", bob)).as("이력 없음").isEqualTo(historyBefore);
		assertThat(count("user_notices", bob)).as("알림 없음").isEqualTo(noticesBefore);
	}

	@Test
	void anAccountWithoutASettingsRowGetsTheOnboardingDefaultsAndNoRowIsCreated() {
		UserId carol = account("carol@example.com");
		this.jdbc.sql("delete from user_tag_preferences where user_id = :user").param("user", carol.value()).update();
		this.jdbc.sql("delete from user_settings where user_id = :user").param("user", carol.value()).update();

		DigestInterests interests = this.directory.interestsOf(carol).orElseThrow();

		assertThat(interests).isEqualTo(new DigestInterests(this.first, List.of(), Set.of(),
				Set.of("brief", "press-release"), Set.of(), "", ZoneId.of("Asia/Seoul"), 40, 3, SummaryLength.NORMAL,
				ReasoningEffort.MEDIUM));
		assertThat(count("user_settings", carol)).isZero();
		assertThat(count("user_tag_preferences", carol)).isZero();
	}

	@Test
	void anUnknownAccountHasNoInterests() {
		account("dave@example.com");

		assertThat(this.directory.interestsOf(new UserId(Long.MAX_VALUE))).isEmpty();
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return ((ProvisionResult.Created) result).user();
	}

	private void preference(UserId user, String kind, String key, String mode) {
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, :kind, :key, :mode, now())
				""").param("user", user.value()).param("kind", kind).param("key", key).param("mode", mode).update();
	}

	private List<Map<String, Object>> preferences(UserId user) {
		return this.jdbc.sql("""
				select tag_kind, tag_key, mode from user_tag_preferences where user_id = :user order by tag_kind, tag_key
				""").param("user", user.value()).query().listOfRows();
	}

	private long count(String table, UserId user) {
		return this.jdbc.sql("select count(*) from " + table + " where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

}
