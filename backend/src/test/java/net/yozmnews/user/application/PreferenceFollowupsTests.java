package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetActivationResult;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.application.TagSetRows;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.notice.Notice;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PreferencesAdjustedContent;
import net.yozmnews.user.domain.preference.Adjustment;
import net.yozmnews.user.domain.preference.AdjustmentKind;
import net.yozmnews.user.domain.preference.PreferenceMode;
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
 * 따라가기 — 운영자가 계보 원문으로 새 체계 버전을 켜면 선호 버전이 옛 버전인 사용자를 모두 활성 키로 고쳐 쓰고, 해제 · 더함을 이력(원인
 * TAG_SET_CHANGE)과 알림으로 남기며, 다시 돌려도 같다. 한 사용자의 실패는 그 사용자만 되돌린다. 활성화가 되돌려지면 리스너에 갈 발행도 없다. 실제
 * PostgreSQL 과 기본 체계(버전 1)에서 시작한다 — 시험이 트랜잭션을 열지 않고, 끝나면 버전 2 이상을 지운다.
 * <p>
 * 새 버전 — 지역 japan → nippon(이름 변경), china 는 계보 없이 지움, 형식 listicle(기본 제외)을 새로 들임.
 * <p>
 * 지키는 규칙: user-R45 · user-R47 · user-R48 · tag-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class PreferenceFollowupsTests {

	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	private static final String LISTENER = "%TagSetActivationListener%";

	@Autowired
	PreferenceFollowups followups;

	@Autowired
	AccountProvisioning provisioning;

	@Autowired
	TagSetAdministration administration;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	NoticeRepository notices;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private TagSetId first;

	@BeforeEach
	void startFromTheFirstVersion() {
		clean();
		this.first = this.tagSets.activeTagSet().id();
	}

	@AfterEach
	void restore() {
		awaitListener();
		clean();
	}

	@Test
	void everyUserOnTheOldVersionFollowsWithHistoryAndOneNotice() {
		UserId alice = account("alice@example.com");
		UserId bob = account("bob@example.com");
		region(alice, "japan");
		region(alice, "china");
		topic(alice, "politics");

		TagSetId second = activate();
		this.followups.followAll();

		assertThat(preferences(alice)).containsExactly(row("FORMAT", "brief"), row("FORMAT", "listicle"),
				row("FORMAT", "press-release"), row("REGION", "nippon"), row("TOPIC", "politics"));
		assertThat(preferences(bob)).containsExactly(row("FORMAT", "brief"), row("FORMAT", "listicle"),
				row("FORMAT", "press-release"));
		assertThat(version(alice)).isEqualTo(second.value());
		assertThat(version(bob)).isEqualTo(second.value());
		assertThat(changeHistory(alice)).containsExactly(history(this.first, "REGION", "china", "REMOVE"),
				history(second, "FORMAT", "listicle", "ADD"));
		assertThat(changeHistory(bob)).containsExactly(history(second, "FORMAT", "listicle", "ADD"));
		assertThat(adjustmentNotices(alice)).containsExactly(new PreferencesAdjustedContent(2,
				List.of(new Adjustment(TagKind.REGION, "china", "중국", PreferenceMode.EXCLUDE,
						AdjustmentKind.RELEASED_REMOVED),
						new Adjustment(TagKind.FORMAT, "listicle", "목록형", PreferenceMode.EXCLUDE,
								AdjustmentKind.ADDED_DEFAULT_EXCLUDED))));
		assertThat(adjustmentNotices(bob)).hasSize(1);

		// 다시 돌려도 같다 — 선호 버전이 활성과 같아 아무것도 쓰지 않는다
		assertThat(this.followups.followAll()).isZero();
		assertThat(changeHistory(alice)).hasSize(2);
		assertThat(adjustmentNotices(alice)).hasSize(1);
	}

	@Test
	void aUserOnTheActiveVersionIsLeftAlone() {
		UserId alice = account("alice@example.com");

		Integer before = historyCount(alice);
		assertThat(this.followups.followAll()).isZero();

		assertThat(historyCount(alice)).isEqualTo(before);
		assertThat(adjustmentNotices(alice)).isEmpty();
		assertThat(version(alice)).isEqualTo(this.first.value());
	}

	@Test
	void oneUsersFailureRollsBackOnlyThatUserAndARerunFollowsTheRest() {
		UserId alice = account("alice@example.com");
		UserId bob = account("bob@example.com");
		// 밥의 선호에 버전 1 에 없는 키를 둔다 — 그 사용자의 따라가기가 실패한다
		region(bob, "atlantis");

		TagSetId second = activate();
		assertThatIllegalArgumentException().isThrownBy(this.followups::followAll);

		assertThat(version(alice)).isEqualTo(second.value());
		assertThat(version(bob)).isEqualTo(this.first.value());
		assertThat(adjustmentNotices(bob)).isEmpty();

		this.jdbc.sql("delete from user_tag_preferences where user_id = :user and tag_key = 'atlantis'")
			.param("user", bob.value())
			.update();
		this.followups.followAll();
		assertThat(version(bob)).isEqualTo(second.value());
		assertThat(adjustmentNotices(bob)).hasSize(1);
	}

	@Test
	void theListenerCommitsUserByUserSoAnEarlierUserStaysFollowedWhenALaterOneFails() {
		UserId alice = account("alice@example.com");
		UserId bob = account("bob@example.com");
		// 밥의 따라가기가 실패한다 — 리스너가 트랜잭션 없이 받아야 앞의 앨리스가 커밋돼 남는다
		region(bob, "atlantis");

		TagSetId second = activate();
		awaitListener();

		assertThat(version(alice)).isEqualTo(second.value());
		assertThat(adjustmentNotices(alice)).hasSize(1);
		assertThat(version(bob)).isEqualTo(this.first.value());
		assertThat(adjustmentNotices(bob)).isEmpty();
	}

	@Test
	void aCommittedActivationIsPublishedToTheListenerAndARolledBackOneIsNot() {
		Long before = publications();

		this.transaction.executeWithoutResult(status -> {
			assertThat(this.administration.activate(secondText(), 0, ChangeActor.OPERATOR))
				.isInstanceOf(TagSetActivationResult.Activated.class);
			status.setRollbackOnly();
		});

		assertThat(publications()).isEqualTo(before);
		assertThat(this.tagSets.activeTagSet().id()).isEqualTo(this.first);

		activate();
		assertThat(publications()).isEqualTo(before + 1);
	}

	private TagSetId activate() {
		TagSetActivationResult result = this.administration.activate(secondText(), 0, ChangeActor.OPERATOR);
		assertThat(result).isInstanceOf(TagSetActivationResult.Activated.class);
		return ((TagSetActivationResult.Activated) result).version().id();
	}

	/** 버전 1 의 원문에서 japan → nippon(계보), china 지움, 형식 listicle(기본 제외) 더함 */
	private String secondText() {
		String text = this.administration.versionText(this.first).orElseThrow();
		text = text.replaceFirst("  - key: \"china\"\n(    .*\n)+?(?=  - key: )", "");
		text = text.replace("key: \"japan\"", "key: \"nippon\"");
		text = text.replace("importance:\n", """
				  - key: "listicle"
				    name: "목록형"
				    description: "항목을 나열한 기사"
				    excludedByDefault: true
				importance:
				""");
		return text + "lineage:\n  - { kind: region, change: rename, from: [japan], to: [nippon] }\n";
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return ((ProvisionResult.Created) result).user();
	}

	private void region(UserId user, String key) {
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'REGION', :key, 'EXCLUDE', now())
				""").param("user", user.value()).param("key", key).update();
	}

	private void topic(UserId user, String key) {
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', :key, 'SUBSCRIBE', now())
				""").param("user", user.value()).param("key", key).update();
	}

	private List<Map<String, Object>> preferences(UserId user) {
		return this.jdbc.sql("""
				select tag_kind, tag_key from user_tag_preferences where user_id = :user order by tag_kind, tag_key
				""").param("user", user.value()).query().listOfRows();
	}

	private static Map<String, Object> row(String kind, String key) {
		return Map.of("tag_kind", kind, "tag_key", key);
	}

	private long version(UserId user) {
		return this.jdbc.sql("select tag_set_id from user_settings where user_id = :user")
			.param("user", user.value())
			.query(Long.class)
			.single();
	}

	private List<Map<String, Object>> changeHistory(UserId user) {
		return this.jdbc.sql("""
				select tag_set_id, tag_kind, tag_key, action from user_tag_preference_events
				where user_id = :user and cause = 'TAG_SET_CHANGE' order by id
				""").param("user", user.value()).query().listOfRows();
	}

	private static Map<String, Object> history(TagSetId version, String kind, String key, String action) {
		return Map.of("tag_set_id", version.value(), "tag_kind", kind, "tag_key", key, "action", action);
	}

	private Integer historyCount(UserId user) {
		return this.jdbc.sql("select count(*) from user_tag_preference_events where user_id = :user")
			.param("user", user.value())
			.query(Integer.class)
			.single();
	}

	private List<PreferencesAdjustedContent> adjustmentNotices(UserId user) {
		return this.notices.page(user, null, 50)
			.stream()
			.map(Notice::content)
			.filter(PreferencesAdjustedContent.class::isInstance)
			.map(PreferencesAdjustedContent.class::cast)
			.toList();
	}

	private Long publications() {
		return this.jdbc.sql("select count(*) from event_publication where listener_id like :listener")
			.param("listener", LISTENER)
			.query(Long.class)
			.single();
	}

	/** 리스너가 끝날 때까지(마치거나 실패할 때까지) 기다린다 — 시험 뒤 버전을 지우기 전에 */
	private void awaitListener() {
		long deadline = System.nanoTime() + 20_000_000_000L;
		while (System.nanoTime() < deadline) {
			Long open = this.jdbc
				.sql("""
					select count(*) from event_publication
					where listener_id like :listener and completion_date is null and coalesce(status, '') <> 'FAILED'
					""")
				.param("listener", LISTENER)
				.query(Long.class)
				.single();
			if (open == 0) {
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
	}

	private void clean() {
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from event_publication where listener_id like :listener")
			.param("listener", LISTENER)
			.update();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

}
