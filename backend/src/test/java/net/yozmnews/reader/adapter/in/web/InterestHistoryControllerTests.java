package net.yozmnews.reader.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import net.yozmnews.user.SignedInUsers;
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
		// 노출 둘에 좋아요 둘 · 구독 더함과 노출 하나는 관심이 드러나고, 반응 없는 노출 하나는 x = 0 이라 드러나지 않는다
		List<Boolean> prominent = JsonPath.read(body, "$.topics[*].prominent");
		assertThat(prominent).containsExactly(true, false, true);
		List<Object> executiveLevels = JsonPath.read(body, "$.topics[0].levels");
		assertThat(executiveLevels).hasSize(13);
		Object firstLevel = JsonPath.read(body, "$.topics[0].levels[0].level");
		assertThat(firstLevel).as("13주 전 — 신호 없음").isNull();
		Double executiveNow = JsonPath.read(body, "$.topics[0].levels[12].level");
		assertThat(executiveNow).isGreaterThan(0.2);
		Double legislatureNow = JsonPath.read(body, "$.topics[1].levels[12].level");
		assertThat(legislatureNow).isEqualTo(0.0);
		List<Map<String, Object>> changes = JsonPath.read(body, "$.periods[*].subscriptionChanges[*]");
		assertThat(changes).singleElement()
			.satisfies(change -> assertThat(change).containsEntry("action", "ADD")
				.containsEntry("topics", List.of("economy.markets")));
		List<List<String>> entered = JsonPath.read(body, "$.periods[*].entered");
		assertThat(entered.stream().flatMap(List::stream).toList()).as("처음 드러난 기간에 한 번씩")
			.containsExactlyInAnyOrder("politics.executive", "economy.markets");
	}

	@Test
	void anUnknownSpanIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/api/interest-history").param("span", "YEARS_2").with(this.reader))
			.andExpect(status().isBadRequest());
	}

}
