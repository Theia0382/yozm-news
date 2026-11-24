package net.yozmnews.reader.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.user.UserTables;
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

/**
 * 독자 화면의 구독 제안 — 응답 모양(태그 체계 버전 ID · 중분류 키 · 이름 · 대분류 이름), 제안이 없으면 빈 목록. 제안의 판정은 digest 의 시험이
 * 고정한다.
 * <p>
 * 실제 PostgreSQL 위에서 행을 SQL 로 넣고, 시각은 지금에서 거슬러 정한다. 로그인 도우미가 만든 사용자는 설정 행이 없어 계정을 만들 때의 값 — 구독한
 * 주제가 없다.
 * <p>
 * 지키는 규칙: reader-R12
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TopicSuggestionControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	JdbcClient jdbc;

	private InterestRows rows;

	private SignedInUsers.SignedInUser reader;

	@BeforeEach
	void signIn() throws Exception {
		this.rows = new InterestRows(this.jdbc);
		clear();
		this.reader = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("topic-suggestion@example.com");
		LlmKeyRows.give(this.jdbc, this.reader.id().value());
	}

	@AfterEach
	void clear() {
		this.rows.clear();
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void aSuggestionCarriesTheTagSetAndTheNames() throws Exception {
		// 하루 전 다이제스트가 입법 기사 둘을 보였고 둘 다 좋아요 — 관심 약 0.394 로 기준 0.35 이상
		Instant shown = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(1));
		long first = this.rows.article("첫 기사", "politics.legislature");
		long second = this.rows.article("둘째 기사", "politics.legislature");
		this.rows.cite(this.reader.id().value(), shown, first, second);
		this.rows.react(this.reader.id().value(), first, "LIKE", shown.plus(Duration.ofMinutes(5)));
		this.rows.react(this.reader.id().value(), second, "LIKE", shown.plus(Duration.ofMinutes(5)));

		this.mockMvc.perform(get("/api/topic-suggestions").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"tagSetId":%d,"topics":[{"key":"politics.legislature","name":"국회·정당·선거","categoryName":"정치"}]}"""
				.formatted(this.tagSets.activeTagSet().id().value()), JsonCompareMode.STRICT));
	}

	@Test
	void withoutSignalsThereIsNothingToSuggest() throws Exception {
		this.mockMvc.perform(get("/api/topic-suggestions").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"tagSetId":%d,"topics":[]}""".formatted(this.tagSets.activeTagSet().id().value()),
					JsonCompareMode.STRICT));
	}

}
