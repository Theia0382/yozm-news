package net.yozmnews.reader.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.reader.UserStorylineSummaryRows;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 독자 화면의 관심사 히스토리 — 고른 기간 · 단위마다의 선택지와 기본값, 기간 수 · 진행 중 기간, 주제 행(이름 · 대분류 이름 · 태그 체계 순 · 드러남),
 * 기간마다 관심 칸(신호 없으면 null) · 생김 · 주제 구독 변경, 틀린 기간의 400. 관심 값 자체는 digest 의 시험이 고정한다.
 * <p>
 * 실제 PostgreSQL 위에서 행을 SQL 로 넣고, 시각은 지금에서 거슬러 정한다. 설정 행이 없는 사용자라 시간대는 계정을 만들 때의 값(Asia/Seoul)이다.
 * <p>
 * 지키는 규칙: reader-R8
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InterestHistoryControllerTests {

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

	private InterestRows rows;

	private SignedInUsers.SignedInUser reader;

	@BeforeEach
	void signIn() throws Exception {
		this.rows = new InterestRows(this.jdbc);
		clear();
		this.reader = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("interest-history@example.com");
		LlmKeyRows.give(this.jdbc, this.reader.id().value());
	}

	@AfterEach
	void clear() {
		this.rows.clear();
		this.jdbc.sql("delete from storylines").update();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void theDefaultIsThirteenWeeksAndEachUnitOffersItsChoices() throws Exception {
		this.mockMvc.perform(get("/api/interest-history").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.span").value("WEEKS_13"))
			.andExpect(content().json("""
					{"spans": [
					  {"unit": "WEEK", "choices": ["WEEKS_13", "WEEKS_26", "WEEKS_52"], "defaultSpan": "WEEKS_13"},
					  {"unit": "MONTH", "choices": ["MONTHS_12", "MONTHS_24", "MONTHS_36"], "defaultSpan": "MONTHS_12"}
					]}
					"""))
			.andExpect(jsonPath("$.periods.length()").value(13))
			.andExpect(jsonPath("$.periods[12].inProgress").value(true))
			.andExpect(jsonPath("$.periods[11].inProgress").value(false))
			.andExpect(jsonPath("$.topics").isEmpty());
		this.mockMvc.perform(get("/api/interest-history").param("span", "MONTHS_24").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.span").value("MONTHS_24"))
			.andExpect(jsonPath("$.periods.length()").value(24));
	}

	@Test
	void topicsCarryNamesInTagSetOrderWithALevelCellForEachPeriod() throws Exception {
		long user = this.reader.id().value();
		Instant shown = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(1));
		long liked1 = this.rows.article("행정 하나", "politics.executive");
		long liked2 = this.rows.article("행정 둘", "politics.executive");
		long markets = this.rows.article("증시", "economy.markets");
		long legislature = this.rows.article("의회", "politics.legislature");
		this.rows.cite(user, shown, markets, liked1, legislature, liked2);
		this.rows.react(user, liked1, "LIKE", shown.plus(Duration.ofMinutes(5)));
		this.rows.react(user, liked2, "LIKE", shown.plus(Duration.ofMinutes(5)));
		this.rows.subscribe(user, "economy.markets", "ADD", shown);

		String body = this.mockMvc.perform(get("/api/interest-history").with(this.reader))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		List<String> keys = JsonPath.read(body, "$.topics[*].key");
		assertThat(keys).as("태그 체계 순 — 정치의 중분류가 경제보다 앞").containsExactly("politics.executive",
				"politics.legislature", "economy.markets");
		List<String> names = JsonPath.read(body, "$.topics[*].name");
		assertThat(names).containsExactly("대통령실·행정부", "국회·정당·선거", "증권·투자");
		List<String> categoryNames = JsonPath.read(body, "$.topics[*].categoryName");
		assertThat(categoryNames).containsExactly("정치", "정치", "경제");
		// 노출 둘에 좋아요 둘은 관심이 드러나고, 반응 없는 노출 하나는 x = 0, 구독 더함과 노출 하나는 x = 0 ÷ 4 + 0.1 = 0.1 —
		// 구독만으로는 관심 있음(0.2)이 아니라 드러나지 않는다
		List<Boolean> prominent = JsonPath.read(body, "$.topics[*].prominent");
		assertThat(prominent).containsExactly(true, false, false);
		List<Object> executiveLevels = JsonPath.read(body, "$.topics[0].levels");
		assertThat(executiveLevels).hasSize(13);
		Object firstLevel = JsonPath.read(body, "$.topics[0].levels[0].level");
		assertThat(firstLevel).as("13주 전 — 신호 없음").isNull();
		// 관심 값은 지금에서 거슬러 정한 시각이라 자릿수가 실행마다 다르다 — JSON 수는 Double 또는 BigDecimal 로 읽혀 Number 로 받는다
		Number executiveNow = JsonPath.read(body, "$.topics[0].levels[12].level");
		assertThat(executiveNow.doubleValue()).isGreaterThan(0.2);
		Number legislatureNow = JsonPath.read(body, "$.topics[1].levels[12].level");
		assertThat(legislatureNow.doubleValue()).isEqualTo(0.0);
		List<Map<String, Object>> changes = JsonPath.read(body, "$.periods[*].subscriptionChanges[*]");
		assertThat(changes).singleElement()
			.satisfies(change -> assertThat(change).containsEntry("action", "ADD")
				.containsEntry("topics", List.of("economy.markets")));
		List<List<String>> entered = JsonPath.read(body, "$.periods[*].entered");
		// 시장은 구독 더함과 반응 없는 노출 하나(x = 0.1) — 관심 있음이 아니라 생기지 않는다
		assertThat(entered.stream().flatMap(List::stream).toList()).as("처음 드러난 기간에 한 번씩")
			.containsExactly("politics.executive");
	}

	/**
	 * 정리된 사용자 스토리라인의 행 — 값과 생김은 그 추적 대상 가운데 ID 가 가장 작은 꼭대기의 것이다. 흐름의 사건 넷을 하루 전에 보이고
	 * 좋아요를 눌러 지금 관심은 4w ÷ (4w + 3) ≈ 0.57(w ≈ 0.977)로 만들 기준 0.35 위다. 정리가 없는 사용자 스토리라인은 행이 없다
	 */
	@Test
	void summarizedUserStorylinesHaveARowWithALevelCellForEachPeriodAndAppearWhenTheyReachTheThreshold() throws Exception {
		long user = this.reader.id().value();
		Instant shown = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(1));
		long flow = UserStorylineFixtures.storyline(this.jdbc, shown);
		long unsummarizedFlow = UserStorylineFixtures.storyline(this.jdbc, shown);
		long[] liked = new long[4];
		for (int i = 0; i < liked.length; i++) {
			liked[i] = this.rows.article("흐름 사건 " + i, "politics.executive");
			link(flow, liked[i]);
		}
		long other = this.rows.article("다른 흐름 사건", "politics.executive");
		link(unsummarizedFlow, other);
		this.rows.cite(user, shown, liked[0], liked[1], liked[2], liked[3], other);
		for (long article : liked) {
			this.rows.react(user, article, "LIKE", shown.plus(Duration.ofMinutes(5)));
		}
		this.rows.react(user, other, "LIKE", shown.plus(Duration.ofMinutes(5)));
		UserStorylineSummaryRows summaries = new UserStorylineSummaryRows(this.jdbc);
		long summarized = summaries.userStoryline(user, flow);
		summaries.summarize(summarized, "정리된 흐름", shown);
		summaries.userStoryline(user, unsummarizedFlow);

		String body = this.mockMvc.perform(get("/api/interest-history").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userStorylines.length()").value(1))
			.andExpect(jsonPath("$.userStorylines[0].id").value(summarized))
			.andExpect(jsonPath("$.userStorylines[0].title").value("정리된 흐름 제목"))
			.andExpect(jsonPath("$.userStorylines[0].levels.length()").value(13))
			// 신호가 생기기 전의 기간은 값이 없고, 진행 중인 기간은 만들 기준 위다
			.andExpect(jsonPath("$.userStorylines[0].levels[0].level").isEmpty())
			.andExpect(jsonPath("$.userStorylines[0].levels[12].level").value(greaterThan(0.5)))
			.andExpect(jsonPath("$.periods[12].leftUserStorylines").isEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		// 보이게 된 기간은 하나다 — 하루 전이 지난 기간이면 그 기간, 아니면 진행 중인 기간
		List<Integer> entered = com.jayway.jsonpath.JsonPath.read(body, "$.periods[*].enteredUserStorylines[*]");
		assertThat(entered).containsExactly((int) summarized);
	}

	@Test
	void aUserWithoutSummariesHasNoUserStorylineRows() throws Exception {
		this.mockMvc.perform(get("/api/interest-history").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userStorylines").isEmpty())
			.andExpect(jsonPath("$.periods[12].enteredUserStorylines").isEmpty())
			.andExpect(jsonPath("$.periods[12].leftUserStorylines").isEmpty());
	}

	private void link(long storyline, long article) {
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, (select event_id from event_articles where article_id = :article), 'MERGE', now())
				""").param("storyline", storyline).param("article", article).update();
	}

	@Test
	void anUnknownSpanIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/api/interest-history").param("span", "YEARS_2").with(this.reader))
			.andExpect(status().isBadRequest());
	}

}
