package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.StorylineSubscriptionAction;
import net.yozmnews.user.StorylineSubscriptionCause;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.subscription.StorylineSubscriptionRepository;
import net.yozmnews.user.domain.subscription.SubscriptionChange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스토리라인 구독 API — 판정 결과는 판별자 하나의 200 이고 손으로 쓴 JSON 과 STRICT 로 견준다. 경로의 스토리라인 ID 가 1 보다 작으면 400
 * 이다. 내 구독 이력은 최근부터 한 쪽과 다음 쪽 커서를 내고, 커서 두 값은 함께 오며 틀리면 400 이다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R52 · user-R63
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StorylineSubscriptionControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	StorylineSubscriptionRepository repository;

	@Autowired
	TransactionTemplate transaction;

	private SignedInUsers.SignedInUser user;

	@BeforeEach
	void signIn() throws Exception {
		reset();
		this.user = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("subscriptions@example.com");
	}

	@AfterEach
	void reset() {
		UserTables.deleteAll(this.jdbc);
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void subscribingAnswersEachOutcome() throws Exception {
		long storyline = storyline();
		long merged = storyline();
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", storyline)
			.param("id", merged)
			.update();

		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", merged).with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"SUBSCRIBED","storylineId":%d,"limit":null}""".formatted(storyline),
					JsonCompareMode.STRICT));
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", storyline).with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ALREADY_SUBSCRIBED","storylineId":%d,"limit":null}""".formatted(storyline),
					JsonCompareMode.STRICT));
		this.mockMvc.perform(put("/api/storyline-subscriptions/999999999").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNKNOWN_STORYLINE","storylineId":null,"limit":null}""", JsonCompareMode.STRICT));
	}

	@Test
	void aFullListAnswersLimitReached() throws Exception {
		for (int i = 0; i < 100; i++) {
			this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", storyline()).with(this.user))
				.andExpect(status().isOk());
		}

		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", storyline()).with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"LIMIT_REACHED","storylineId":null,"limit":100}""", JsonCompareMode.STRICT));
	}

	@Test
	void theListCarriesTheFlowItsSeedsStateAndTitleAndUnsubscribingAnswersEachOutcome() throws Exception {
		long storyline = storyline();
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", storyline).with(this.user));
		long userStoryline = this.jdbc.sql("select id from user_storylines").query(Long.class).single();

		this.mockMvc.perform(get("/api/storyline-subscriptions").with(this.user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subscriptions[0].subscribedAt").isString())
			.andExpect(jsonPath("$.subscriptions[0].length()").value(6))
			.andExpect(content().json("""
					{"subscriptions":[{"userStorylineId":%d,"headStorylineId":%d,"storylineIds":[%d],"state":"ACTIVE",
					                   "title":null}],
					 "limit":100}""".formatted(userStoryline, storyline, storyline)));
		this.mockMvc.perform(delete("/api/storyline-subscriptions/{id}", storyline).with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNSUBSCRIBED"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(delete("/api/storyline-subscriptions/{id}", storyline).with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"NOT_SUBSCRIBED"}""", JsonCompareMode.STRICT));
	}

	@Test
	void aFlowTrackedFromTwoStorylinesIsOneLineOfTheList() throws Exception {
		long earlier = storyline();
		long later = storyline();
		long alone = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		// 대조 — 씨앗 하나인 흐름은 한 줄이다
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", alone).with(this.user));
		this.mockMvc.perform(get("/api/storyline-subscriptions").with(this.user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subscriptions.length()").value(1));

		// 한 흐름의 스토리라인 둘을 눌러도 줄은 하나 늘 뿐이다
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", earlier).with(this.user));
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", later).with(this.user));

		assertThat(this.jdbc.sql("select count(*) from user_storyline_tracked_seeds").query(Integer.class).single())
			.as("추적 씨앗은 셋이다").isEqualTo(3);
		this.mockMvc.perform(get("/api/storyline-subscriptions").with(this.user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subscriptions.length()").value(2));
	}

	@Test
	void theTrackingStatesAnswerEachAskedStorylineInOrder() throws Exception {
		long earlier = storyline();
		long later = storyline();
		long other = storyline();
		UserStorylineFixtures.follows(this.jdbc, earlier, later);
		this.mockMvc.perform(put("/api/storyline-subscriptions/{id}", earlier).with(this.user));

		// 앞 흐름을 추적하면 뒤 흐름도 추적 중이다. 없는 스토리라인 · 관계없는 스토리라인은 아니고, 같은 ID 는 처음 자리 한 번이다
		this.mockMvc
			.perform(get("/api/storyline-subscriptions/states")
				.param("ids", later + "," + other + ",999999999," + earlier + "," + later)
				.with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"states":[{"storylineId":%d,"tracked":true},{"storylineId":%d,"tracked":false},
					           {"storylineId":999999999,"tracked":false},{"storylineId":%d,"tracked":true}]}"""
				.formatted(later, other, earlier), JsonCompareMode.STRICT));
		// 목록의 줄은 누른 스토리라인과 그것이 든 사용자 스토리라인 · 그 헤드(이어진 뒤 흐름)를 싣는다
		long userStoryline = this.jdbc.sql("select id from user_storylines").query(Long.class).single();
		this.mockMvc.perform(get("/api/storyline-subscriptions").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"subscriptions":[{"storylineIds":[%d],"userStorylineId":%d,"headStorylineId":%d}],"limit":100}"""
				.formatted(earlier, userStoryline, later)));
	}

	@Test
	void brokenTrackingStateIdsAreABadRequest() throws Exception {
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").param("ids", "0").with(this.user))
			.andExpect(status().isBadRequest());
		String tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
			.mapToObj(String::valueOf)
			.collect(java.util.stream.Collectors.joining(","));
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").param("ids", tooMany).with(this.user))
			.andExpect(status().isBadRequest());
		// 대조 — 100개는 받는다
		String atLimit = java.util.stream.IntStream.rangeClosed(1, 100)
			.mapToObj(String::valueOf)
			.collect(java.util.stream.Collectors.joining(","));
		this.mockMvc.perform(get("/api/storyline-subscriptions/states").param("ids", atLimit).with(this.user))
			.andExpect(status().isOk());
	}

	@Test
	void aStorylineIdBelowOneIsABadRequest() throws Exception {
		this.mockMvc.perform(put("/api/storyline-subscriptions/0").with(this.user)).andExpect(status().isBadRequest());
		this.mockMvc.perform(delete("/api/storyline-subscriptions/0").with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(put("/api/storyline-subscriptions/abc").with(this.user))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theHistoryAnswersAPageAndAcceptsTheCursorOfTheLastRow() throws Exception {
		long removed = storyline();
		long kept = storyline();
		record(removed, StorylineSubscriptionAction.ADD, "2026-11-10T00:00:00Z");
		long newer = record(kept, StorylineSubscriptionAction.REMOVE, "2026-11-11T00:00:00Z");
		this.jdbc.sql("delete from storylines where id = :id").param("id", removed).update();

		this.mockMvc.perform(get("/api/storyline-subscriptions/changes").with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"changes":[
					  {"storylineId":%d,"action":"REMOVE","occurredAt":"2026-11-11T00:00:00Z"},
					  {"storylineId":null,"action":"ADD","occurredAt":"2026-11-10T00:00:00Z"}],
					 "next":null}""".formatted(kept), JsonCompareMode.STRICT));
		// 짝이 맞는 커서는 그보다 앞선 행만 낸다
		this.mockMvc
			.perform(get("/api/storyline-subscriptions/changes").param("beforeAt", "2026-11-11T00:00:00Z")
				.param("beforeId", String.valueOf(newer))
				.with(this.user))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"changes":[{"storylineId":null,"action":"ADD","occurredAt":"2026-11-10T00:00:00Z"}],
					 "next":null}""", JsonCompareMode.STRICT));
	}

	@Test
	void aBrokenHistoryCursorIsABadRequest() throws Exception {
		this.mockMvc
			.perform(get("/api/storyline-subscriptions/changes").param("beforeAt", "2026-11-11T00:00:00Z")
				.with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/storyline-subscriptions/changes").param("beforeId", "3").with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/api/storyline-subscriptions/changes").param("beforeAt", "2026-11-11T00:00:00Z")
				.param("beforeId", "0")
				.with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/api/storyline-subscriptions/changes").param("beforeAt", "x")
				.param("beforeId", "3")
				.with(this.user))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/api/storyline-subscriptions/changes").param("beforeAt", "2026-11-11T00:00:00Z")
				.param("beforeId", "abc")
				.with(this.user))
			.andExpect(status().isBadRequest());
	}

	/** 세션 사용자의 원인이 사용자인 이력 행 하나 — 그 행 ID */
	private long record(long storyline, StorylineSubscriptionAction action, String at) {
		this.transaction.executeWithoutResult(status -> this.repository.record(this.user.id(),
				List.of(new SubscriptionChange(storyline, action)), StorylineSubscriptionCause.USER,
				Instant.parse(at)));
		return this.jdbc.sql("select max(id) from user_storyline_subscription_events").query(Long.class).single();
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", Instant.now().atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

}
