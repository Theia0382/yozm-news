package net.yozmnews.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
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
import org.jobrunr.scheduling.JobScheduler;
import net.yozmnews.digest.DigestRounds;

/**
 * 독자 화면의 사용자 격리 — 목록 · 카드는 사용자 데이터가 없어 두 사용자가 같은 본문을 받고, 타임라인의 받은 날짜는 그 사용자의 다이제스트에서만 온다. 세
 * 경로 모두 세션 없이 401, 키가 없으면 403(키 관문)이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · reader-R5
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class StorylineScreenScopeTests {

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

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@BeforeEach
	@AfterEach
	void reset() {
		this.rows = new ReaderRows(this.jdbc);
		this.rows.clear();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void sharedResponsesAreTheSameForEveryoneAndReceivedDatesAreOnlyTheirOwn() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("reader-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("reader-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		Instant at = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(2));
		long event = this.rows.event(1, at.toString(), at.toString());
		long article = this.rows.article("격리 시험 기사");
		this.rows.assign(article, event, at.toString());
		long storyline = this.rows.storyline(at.toString(), at.toString(), event);
		this.rows.member(storyline, event);
		// A 만 그 기사를 받았다
		this.rows.cite(this.rows.digest(a.id().value(), "2026-11-05"), article);

		String cardsA = body("/api/storyline-cards?ids=" + storyline, a);
		// 빈 본문끼리 같아 통과하지 않게 행이 있음을 먼저 본다
		assertThat(cardsA).contains("\"requestedId\":" + storyline);
		assertThat(body("/api/storyline-cards?ids=" + storyline, b)).isEqualTo(cardsA);
		this.mockMvc.perform(get("/api/storylines/{id}", storyline).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.events[0].receivedOn").value("2026-11-05"));
		this.mockMvc.perform(get("/api/storylines/{id}", storyline).with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.events[0].receivedOn").isEmpty());
	}

	@Test
	void everyPathNeedsASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("reader-keyless@example.com");

		for (String path : new String[] { "/api/storylines/1", "/api/storyline-cards?ids=1" }) {
			this.mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			this.mockMvc.perform(get(path).with(keyless))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
		}
	}

	private String body(String path, SignedInUsers.SignedInUser user) throws Exception {
		return this.mockMvc.perform(get(path).with(user))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

}
