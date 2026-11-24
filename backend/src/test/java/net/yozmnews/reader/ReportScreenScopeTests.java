package net.yozmnews.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.UserTables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 변화 리포트 화면의 사용자 격리 — 사용자 A 의 리포트는 A 의 목록에만 있고, B 의 세션으로 A 의 리포트 ID 를 부르면 없는 것(404)이다. 세션
 * 없이 401, 키가 없으면 403(키 관문)이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: account-R5 · account-R6 · docs/specs/reader.md reader-R15 · reader-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
public class ReportScreenScopeTests {

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

	private ReportRows reports;

	@BeforeEach
	@AfterEach
	void reset() {
		this.reports = new ReportRows(this.jdbc);
		this.reports.clear();
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void aUsersReportsAreSeenOnlyThroughTheirOwnSession() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("report-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("report-b@example.com");
		LlmKeyRows.give(this.jdbc, a.id().value());
		LlmKeyRows.give(this.jdbc, b.id().value());
		long ofA = this.reports.report(a.id().value(), "READY", null);
		this.reports.section(ofA, "OVERVIEW", 0, null, "A 의 개요");
		long ofB = this.reports.report(b.id().value(), "READY", null);
		this.reports.section(ofB, "OVERVIEW", 0, null, "B 의 개요");

		// 빈 응답끼리 같아 통과하지 않게 A 의 응답에 행이 있음을 먼저 본다
		this.mockMvc.perform(get("/api/reports").param("kind", "WEEK").with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports.length()").value(1))
			.andExpect(jsonPath("$.reports[0].id").value(ofA));
		this.mockMvc.perform(get("/api/reports/{id}", ofA).with(a))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sections[0].body").value("A 의 개요"));

		this.mockMvc.perform(get("/api/reports").param("kind", "WEEK").with(b))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports.length()").value(1))
			.andExpect(jsonPath("$.reports[0].id").value(ofB));
		this.mockMvc.perform(get("/api/reports/{id}", ofA).with(b)).andExpect(status().isNotFound());
		String ofBDetail = this.mockMvc.perform(get("/api/reports/{id}", ofB).with(b))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(ofBDetail).contains("B 의 개요").doesNotContain("A 의 개요");
	}

	@Test
	void everyPathNeedsASessionAndAKey() throws Exception {
		SignedInUsers.SignedInUser keyless = new SignedInUsers(this.mockMvc, this.accounts, this.credentials,
				this.hashing)
			.signIn("report-keyless@example.com");

		for (String path : new String[] { "/api/reports?kind=WEEK", "/api/reports/1" }) {
			this.mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			this.mockMvc.perform(get(path).with(keyless))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.type").value("urn:yozmnews:problem:llm-key-required"));
		}
	}

}
