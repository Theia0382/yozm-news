package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetActivationResult;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionCause;
import net.yozmnews.user.TopicSubscriptionChange;
import net.yozmnews.user.TopicSubscriptionChanges;
import net.yozmnews.user.TopicSubscriptionHistory;
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
 * 주제 구독 이력 조회 — 선호 이력의 주제 행만 기간(반열림)으로 읽어, 기록 당시 버전의 구조로 펼치고 계보로 활성 버전의 중분류로 푼다. 쓰지 않는다.
 * 실제 PostgreSQL 과 기본 체계(버전 1)에서 시작하고, 버전 2 는 경제 중분류 finance → banking(계보), crypto 계보 없이 지움이다.
 * <p>
 * 기대 키는 기본 체계 파일(tag-sets/default.yaml)의 경제 중분류에서 손으로 옮겨 적은 것이다 — 펼치기 · 계보 규칙을 시험 안에서 불러 기대값을
 * 만들지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TopicSubscriptionHistoryServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

	private static final Instant FROM = Instant.parse("2026-11-10T00:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-20T00:00:00Z");

	/** 버전 1 의 경제 중분류 전체 */
	private static final Set<String> ECONOMY_V1 = Set.of("economy.macro", "economy.finance", "economy.markets",
			"economy.real-estate", "economy.consumer", "economy.crypto");

	/** 버전 1 의 경제를 버전 2 로 푼 것 — finance 는 banking 으로, crypto 는 빠진다 */
	private static final Set<String> ECONOMY_V2 = Set.of("economy.macro", "economy.banking", "economy.markets",
			"economy.real-estate", "economy.consumer");

	@Autowired
	TopicSubscriptionHistory history;

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

	private SecondTagSet fixture;

	@BeforeEach
	void startFromTheFirstVersion() {
		this.fixture = new SecondTagSet(this.administration, this.jdbc);
		this.fixture.clean();
		this.first = this.tagSets.activeTagSet().id();
	}

	@AfterEach
	void restore() {
		this.fixture.awaitListener();
		this.fixture.clean();
	}

	@Test
	void topicRowsOfThePeriodAreExpandedInTheirVersionAndCarryActionAndCause() {
		UserId alice = account("alice@example.com");
		UserId frank = account("frank@example.com");
		// 넣는 순서가 시각 순 · 키 순과 다르다 — 같은 시각 두 행은 행 순(tech.ai 가 먼저), 이른 시각 행은 나중에 넣는다
		event(alice, "TAG_SET_CHANGE", this.first, "TOPIC", "tech.ai", "SUBSCRIBE", "ADD", "2026-11-12T00:00:00Z");
		event(alice, "USER", this.first, "TOPIC", "politics.executive", "SUBSCRIBE", "REMOVE", "2026-11-12T00:00:00Z");
		event(alice, "USER", this.first, "TOPIC", "economy", "SUBSCRIBE", "ADD", "2026-11-11T00:00:00Z");
		event(alice, "USER", this.first, "REGION", "us", "EXCLUDE", "ADD", "2026-11-13T00:00:00Z");
		event(frank, "USER", this.first, "TOPIC", "society", "SUBSCRIBE", "ADD", "2026-11-11T00:00:00Z");

		TopicSubscriptionChanges changes = this.history.changes(alice, FROM, TO);

		assertThat(changes).isEqualTo(new TopicSubscriptionChanges(this.first, List.of(
				new TopicSubscriptionChange("economy", this.first, ECONOMY_V1, TopicSubscriptionAction.ADD,
						TopicSubscriptionCause.USER, Instant.parse("2026-11-11T00:00:00Z")),
				new TopicSubscriptionChange("tech.ai", this.first, Set.of("tech.ai"), TopicSubscriptionAction.ADD,
						TopicSubscriptionCause.TAG_SET_CHANGE, Instant.parse("2026-11-12T00:00:00Z")),
				new TopicSubscriptionChange("politics.executive", this.first, Set.of("politics.executive"),
						TopicSubscriptionAction.REMOVE, TopicSubscriptionCause.USER,
						Instant.parse("2026-11-12T00:00:00Z")))));
	}

	@Test
	void thePeriodIsHalfOpen() {
		UserId bob = account("bob@example.com");
		event(bob, "USER", this.first, "TOPIC", "tech.ai", "SUBSCRIBE", "ADD", "2026-11-09T23:59:59Z");
		event(bob, "USER", this.first, "TOPIC", "tech.game", "SUBSCRIBE", "ADD", "2026-11-10T00:00:00Z");
		event(bob, "USER", this.first, "TOPIC", "tech.science", "SUBSCRIBE", "ADD", "2026-11-20T00:00:00Z");

		TopicSubscriptionChanges changes = this.history.changes(bob, FROM, TO);

		assertThat(changes.changes()).extracting(TopicSubscriptionChange::recordedKey).containsExactly("tech.game");
		assertThat(this.history.changes(bob, FROM, FROM).changes()).as("빈 기간").isEmpty();
	}

	@Test
	void rowsOfAnOlderVersionAreResolvedToTheActiveSubtopics() {
		UserId carol = account("carol@example.com");
		event(carol, "USER", this.first, "TOPIC", "economy", "SUBSCRIBE", "ADD", "2026-11-11T00:00:00Z");
		event(carol, "USER", this.first, "TOPIC", "economy.finance", "SUBSCRIBE", "REMOVE", "2026-11-12T00:00:00Z");
		event(carol, "USER", this.first, "TOPIC", "economy.crypto", "SUBSCRIBE", "ADD", "2026-11-13T00:00:00Z");
		TagSetId second = activateSecond();

		TopicSubscriptionChanges changes = this.history.changes(carol, FROM, TO);

		assertThat(changes.tagSet()).isEqualTo(second);
		assertThat(changes.changes()).extracting(TopicSubscriptionChange::subtopics)
			.containsExactly(ECONOMY_V2, Set.of("economy.banking"), Set.of());
		assertThat(changes.changes()).extracting(TopicSubscriptionChange::recordedVersion)
			.containsOnly(this.first);
	}

	@Test
	void anUnknownAccountHasNoChanges() {
		account("dave@example.com");

		assertThat(this.history.changes(new UserId(Long.MAX_VALUE), FROM, TO))
			.isEqualTo(new TopicSubscriptionChanges(this.first, List.of()));
	}

	@Test
	void aPeriodStartingAfterItsEndIsRejected() {
		UserId erin = account("erin@example.com");

		assertThatThrownBy(() -> this.history.changes(erin, TO, FROM)).isInstanceOf(IllegalArgumentException.class);
	}

	/** 버전 2 를 켠다 — 경제 finance → banking(계보), crypto 계보 없이 지움 */
	private TagSetId activateSecond() {
		String text = this.administration.versionText(this.first).orElseThrow();
		text = text.replaceFirst("      - key: \"crypto\"\n(        .*\n)+?(?=  - key: )", "");
		text = text.replace("key: \"finance\"", "key: \"banking\"");
		text = text + "lineage:\n  - { kind: topic, change: rename, from: [economy.finance], to: [economy.banking] }\n";
		TagSetActivationResult result = this.administration.activate(text, 0, ChangeActor.OPERATOR);
		assertThat(result).isInstanceOf(TagSetActivationResult.Activated.class);
		this.fixture.awaitListener();
		return ((TagSetActivationResult.Activated) result).version().id();
	}

	private UserId account(String email) {
		ProvisionResult result = this.transaction.execute(status -> this.provisioning.provision(Onboarding.OPEN_SIGN_UP,
				EmailAddress.parse(email).orElseThrow(), new DisplayName("사용자"),
				PasswordPolicy.normalize("correct horse battery"), NOW));
		assertThat(result).isInstanceOf(ProvisionResult.Created.class);
		return ((ProvisionResult.Created) result).user();
	}

	private void event(UserId user, String cause, TagSetId version, String kind, String key, String mode,
			String action, String at) {
		this.jdbc.sql("""
				insert into user_tag_preference_events (user_id, cause, tag_set_id, tag_kind, tag_key, mode, action,
				  occurred_at)
				values (:user, :cause, :version, :kind, :key, :mode, :action, :at)
				""")
			.param("user", user.value())
			.param("cause", cause)
			.param("version", version.value())
			.param("kind", kind)
			.param("key", key)
			.param("mode", mode)
			.param("action", action)
			.param("at", Timestamp.from(Instant.parse(at)))
			.update();
	}

}
