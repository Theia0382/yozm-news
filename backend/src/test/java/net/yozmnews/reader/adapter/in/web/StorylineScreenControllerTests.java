package net.yozmnews.reader.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.jobrunr.scheduling.JobScheduler;
import net.yozmnews.digest.DigestRounds;

/**
 * 독자 화면의 공용 스토리라인 — 타임라인(사건 순서 · 직접 소속 · 대표 기사 · 관계 넷 ·
 * 받은 날짜 · 합쳐진 ID), 카드 여럿, 요청의 400 · 404. 응답 객체마다 키 집합을 STRICT 로 본다 — 판정 근거 · 보도 강도 · 요약문이 새지 않는다.
 * <p>
 * 실제 PostgreSQL 위에서 행을 SQL 로 넣는다. 시각은 지금에서 거슬러 정한다(상태가 진행으로 정해지게). 기사의 출처 이름은 이름 있는 피드가 없어 비어
 * 있다 — 엮는 규칙은 {@code article} 의 시험이 본다.
 * <p>
 * 지키는 규칙: reader-R3 ~ reader-R7
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StorylineScreenControllerTests {

	@Autowired
	JobScheduler digestJobs;

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

	private ReaderRows rows;

	private SignedInUsers.SignedInUser reader;

	/** 기준 시각 — 지금을 시 단위로 자르고 30시간 거슬러 간다 */
	private Instant t0;

	private long s;

	private long c;

	private long p;

	private long f;

	private long m;

	private long e1;

	private long e2;

	private long e3;

	private long a1;

	private long a2;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@BeforeEach
	void startFromTheFixture() throws Exception {
		this.rows = new ReaderRows(this.jdbc);
		clear();
		this.reader = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("reader@example.com");
		LlmKeyRows.give(this.jdbc, this.reader.id().value());
		this.t0 = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(30));
		// S — 사건 e1(기사 둘) · e2(기사 하나), 세 기사 모두 중요도 5 · 같은 소스 → 하루 기간의 보도 강도 15 로 큰 스토리라인
		this.e1 = this.rows.event(2, at(1), at(2));
		this.a1 = this.rows.article("국회 본회의 통과", 5);
		this.a2 = this.rows.article("본회의 표결 뒤 반응", 5);
		this.rows.assign(this.a1, this.e1, at(1));
		this.rows.assign(this.a2, this.e1, at(2));
		this.e2 = this.rows.event(1, at(3), at(3));
		long a3 = this.rows.article("법안 공포", 5);
		this.rows.assign(a3, this.e2, at(3));
		this.s = this.rows.storyline(at(1), at(3), this.e1);
		this.rows.member(this.s, this.e1);
		this.rows.member(this.s, this.e2);
		// C — S 의 하위(포함). 사건 e3 은 S 의 타임라인에 물려받은 사건으로 나온다
		this.e3 = this.rows.event(1, at(5), at(5));
		long a4 = this.rows.article("시행령 입법 예고");
		this.rows.assign(a4, this.e3, at(5));
		this.c = this.rows.storyline(at(5), at(5), this.e3);
		this.rows.member(this.c, this.e3);
		this.rows.relate("CONTAINS", this.s, this.c);
		// P — S 를 낳은 앞 흐름(기간 밖), F — S 가 낳은 이어진 흐름
		long e4 = this.rows.event(1, at(-14), at(-14));
		long a5 = this.rows.article("법안 발의");
		this.rows.assign(a5, e4, at(-14));
		this.p = this.rows.storyline(at(-14), at(-14), e4);
		this.rows.member(this.p, e4);
		this.rows.relate("FOLLOWS", this.p, this.s);
		long e5 = this.rows.event(1, at(7), at(7));
		long a6 = this.rows.article("헌법 소원 제기");
		this.rows.assign(a6, e5, at(7));
		this.f = this.rows.storyline(at(7), at(7), e5);
		this.rows.member(this.f, e5);
		this.rows.relate("FOLLOWS", this.s, this.f);
		// M — S 에 합쳐진 스토리라인
		this.m = this.rows.storyline(at(1), at(1), this.e1);
		this.rows.merge(this.m, this.s);
		// 받은 소식 — 11-04 다이제스트가 a1 을, 11-05 다이제스트가 a2 를 인용했다 → e1 은 이른 11-04
		this.rows.cite(this.rows.digest(this.reader.id().value(), "2026-11-04"), this.a1);
		this.rows.cite(this.rows.digest(this.reader.id().value(), "2026-11-05"), this.a2);
	}

	@AfterEach
	void endWithoutTheFixture() {
		clear();
	}

	@Test
	void theTimelineCarriesTheEventsTheRelationsAndTheReceivedDates() throws Exception {
		JsonNode timeline = json("/api/storylines/" + this.s);

		assertKeys(timeline, "storyline", "parent", "children", "preceding", "following", "events");
		JSONAssert.assertEquals("""
				{"id":%d,
				 "title":{"title":"국회 본회의 통과","sources":[],"publishedAt":"2026-11-05T00:00:00Z","url":"%s"},
				 "state":"ACTIVE","eventCount":3,"articleCount":4,"firstArticleAt":"%s","lastArticleAt":"%s",
				 "parentId":null}
				""".formatted(this.s, url(this.a1), at(1), at(5)), timeline.path("storyline").toString(),
				JSONCompareMode.STRICT);
		JSONAssert.assertEquals("""
				{"parent":null,
				 "children":[{"id":%d,"title":"시행령 입법 예고","state":"ACTIVE"}],
				 "preceding":[{"id":%d,"title":"법안 발의","state":"ACTIVE"}],
				 "following":[{"id":%d,"title":"헌법 소원 제기","state":"ACTIVE"}]}
				""".formatted(this.c, this.p, this.f), timeline.toString(), JSONCompareMode.LENIENT);
		// 관계 상대의 키는 셋뿐이다
		assertKeys(timeline.path("children").get(0), "id", "title", "state");
		// 사건 — 첫 기사 시각 순, e3 은 하위에서 물려받았다. 받은 날짜는 e1 만(두 다이제스트 가운데 이른 11-04)
		JsonNode events = timeline.path("events");
		assertThat(events.size()).isEqualTo(3);
		JSONAssert.assertEquals("""
				{"eventId":%d,"direct":true,"articleCount":2,"firstArticleAt":"%s","lastArticleAt":"%s",
				 "representative":{"title":"국회 본회의 통과","sources":[],"publishedAt":"2026-11-05T00:00:00Z","url":"%s"},
				 "receivedOn":"2026-11-04"}
				""".formatted(this.e1, at(1), at(2), url(this.a1)), events.get(0).toString(), JSONCompareMode.STRICT);
		JSONAssert.assertEquals("""
				{"eventId":%d,"direct":true,"receivedOn":null}
				""".formatted(this.e2), events.get(1).toString(), JSONCompareMode.LENIENT);
		JSONAssert.assertEquals("""
				{"eventId":%d,"direct":false,"receivedOn":null}
				""".formatted(this.e3), events.get(2).toString(), JSONCompareMode.LENIENT);
		// 하위의 상위는 S
		JSONAssert.assertEquals("""
				{"parent":{"id":%d,"title":"국회 본회의 통과","state":"ACTIVE"},"children":[],"preceding":[],"following":[]}
				""".formatted(this.s), json("/api/storylines/" + this.c).toString(), JSONCompareMode.LENIENT);
		assertKeys(json("/api/storylines/" + this.c).path("parent"), "id", "title", "state");
	}

	@Test
	void aMergedIdLeadsToItsSurvivorAndAnUnknownIdIsNotFound() throws Exception {
		assertThat(json("/api/storylines/" + this.m).path("storyline").path("id").asLong()).isEqualTo(this.s);
		this.mockMvc.perform(get("/api/storylines/999999999").with(this.reader)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/storylines/0").with(this.reader)).andExpect(status().isNotFound());
	}

	@Test
	void cardsFollowTheRequestedOrderOnceEachAndLeaveOutUnknownIds() throws Exception {
		JsonNode cards = json("/api/storyline-cards?ids=" + this.c + "," + this.m + "," + this.c + ",999999999");

		assertKeys(cards, "cards");
		assertThat(cards.path("cards").size()).isEqualTo(2);
		JSONAssert.assertEquals("""
				{"requestedId":%d,"storyline":{"id":%d,"title":{"title":"시행령 입법 예고"}}}
				""".formatted(this.c, this.c), cards.path("cards").get(0).toString(), JSONCompareMode.LENIENT);
		// 합쳐진 ID 는 받은 ID 와 남는 스토리라인의 카드
		JSONAssert.assertEquals("""
				{"requestedId":%d,"storyline":{"id":%d}}
				""".formatted(this.m, this.s), cards.path("cards").get(1).toString(), JSONCompareMode.LENIENT);
		assertKeys(cards.path("cards").get(0), "requestedId", "storyline");
	}

	@Test
	void cardsRejectNoIdsTooManyIdsAndIdsBelowOne() throws Exception {
		List<String> many = new ArrayList<>();
		for (int i = 0; i < 101; i++) {
			many.add(String.valueOf(this.s));
		}
		this.mockMvc.perform(get("/api/storyline-cards?ids=" + String.join(",", many)).with(this.reader))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/storyline-cards?ids=").with(this.reader)).andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/storyline-cards?ids=" + this.s + ",0").with(this.reader))
			.andExpect(status().isBadRequest());
	}

	@Test
	void noResponseCarriesJudgementGroundsCoverageIntensityOrSummaries() throws Exception {
		List<String> keys = new ArrayList<>();
		collectKeys(json("/api/storylines/" + this.s), keys);
		collectKeys(json("/api/storyline-cards?ids=" + this.s), keys);

		// 빈 응답이 통과하지 않게 모은 키 수의 하한을 먼저 본다
		assertThat(keys).hasSizeGreaterThan(50);
		assertThat(keys).doesNotContain("score", "probability", "cosine", "intensity", "summary", "importance",
				"importanceSum", "judgement", "criteria");
	}

	private void clear() {
		this.rows.clear();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	private String at(int hours) {
		return this.t0.plus(Duration.ofHours(hours)).toString();
	}

	private String url(long article) {
		return this.jdbc.sql("select canonical_url from articles where id = :id")
			.param("id", article)
			.query(String.class)
			.single();
	}

	private JsonNode json(String path) throws Exception {
		String body = this.mockMvc.perform(get(path).with(this.reader))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonMapper.builder().build().readTree(body);
	}

	private static void assertKeys(JsonNode node, String... keys) {
		assertThat(node.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(keys);
	}

	private static void collectKeys(JsonNode node, List<String> keys) {
		if (node.isObject()) {
			node.properties().forEach(entry -> {
				keys.add(entry.getKey());
				collectKeys(entry.getValue(), keys);
			});
		}
		else if (node.isArray()) {
			node.forEach(child -> collectKeys(child, keys));
		}
	}

}
