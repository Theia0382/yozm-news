package net.yozmnews.reader.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.reader.DigestScreenRows;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.jobrunr.scheduling.JobScheduler;
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
 * 독자 화면의 다이제스트 — 홈의 날짜 보기와 수동 생성 상태, 인용 기사의 정보는 {@code article} 의 값(지운 기사의 인용은 빠짐), 가장 최근 시도의
 * 실패 코드 · 갈래(실패 메시지는 내지 않음), 날짜의 사용량, 날짜 목록의 쪽, 수동 생성 결과 넷, 요청의 400 · 404.
 * <p>
 * 실제 PostgreSQL 위에서 행을 SQL 로 넣는다. 기사의 출처 이름은 이름 있는 피드가 없어 비어 있다 — 엮는 규칙은 {@code article} 의 시험이 본다.
 * 설정 행이 없는 사용자라 시간대는 계정을 만들 때의 값(Asia/Seoul)이다. 생성 작업은 돌지 않는다(작업 서버 끔).
 * <p>
 * 지키는 규칙: reader-R7 · reader-R9 ~ reader-R11
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestScreenControllerTests {

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

	private DigestScreenRows rows;

	private ReaderRows readerRows;

	private SignedInUsers.SignedInUser reader;

	@BeforeEach
	void signIn() throws Exception {
		DigestRounds.stop(this.digestJobs);
		this.rows = new DigestScreenRows(this.jdbc);
		this.readerRows = new ReaderRows(this.jdbc);
		clear();
		this.reader = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("digest-screen@example.com");
		LlmKeyRows.give(this.jdbc, this.reader.id().value());
	}

	@AfterEach
	void clear() {
		this.rows.clear();
		this.readerRows.clear();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void theHomeCarriesTheLatestDateWithArticleInformationFromArticles() throws Exception {
		long user = this.reader.id().value();
		long kept = this.readerRows.article("남은 기사 제목");
		long removed = 999_999_999L;
		long ready = this.rows.digest(user, "2026-11-05", "READY", 100, 20, null);
		this.rows.section(ready, "OVERVIEW", 0, null, "오늘의 흐름");
		long story = this.rows.section(ready, "TOP_STORY", 1, "주요 뉴스 제목", "주요 뉴스 요약");
		this.rows.cite(story, 0, removed, null, "SUBSCRIBED");
		this.rows.cite(story, 1, kept, null, "MAJOR_STORYLINE");
		long more = this.rows.section(ready, "MORE_HEADLINES", 2, null, null);
		this.rows.cite(more, 0, kept, "한 줄 코멘트", "INTEREST");
		long failed = this.rows.digest(user, "2026-11-05", "FAILED", 50, 0, "NO_CITATION");
		this.rows.digest(user, "2026-11-04", "EMPTY", null, null, null);
		String url = this.jdbc.sql("select canonical_url from articles where id = :id")
			.param("id", kept)
			.query(String.class)
			.single();

		String body = this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.day.date").value("2026-11-05"))
			.andExpect(jsonPath("$.day.previousDate").value("2026-11-04"))
			.andExpect(jsonPath("$.day.nextDate").isEmpty())
			.andExpect(jsonPath("$.day.shown.digestId").value(ready))
			.andExpect(jsonPath("$.day.shown.periodStart").value("2026-11-04T15:00:00Z"))
			.andExpect(jsonPath("$.day.shown.periodEnd").value("2026-11-05T15:00:00Z"))
			.andExpect(jsonPath("$.day.shown.provider").value("openai"))
			.andExpect(jsonPath("$.day.shown.model").value("m"))
			.andExpect(jsonPath("$.day.shown.candidateCount").value(30))
			.andExpect(jsonPath("$.day.shown.inputTokens").value(100))
			.andExpect(jsonPath("$.day.shown.outputTokens").value(20))
			.andExpect(jsonPath("$.day.shown.sections.length()").value(3))
			.andExpect(jsonPath("$.day.shown.sections[0].kind").value("OVERVIEW"))
			.andExpect(jsonPath("$.day.shown.sections[0].body").value("오늘의 흐름"))
			.andExpect(jsonPath("$.day.shown.sections[0].citations").isEmpty())
			.andExpect(jsonPath("$.day.shown.sections[1].kind").value("TOP_STORY"))
			.andExpect(jsonPath("$.day.shown.sections[1].title").value("주요 뉴스 제목"))
			.andExpect(jsonPath("$.day.shown.sections[1].body").value("주요 뉴스 요약"))
			// 지운 기사의 인용은 빠진다
			.andExpect(jsonPath("$.day.shown.sections[1].citations.length()").value(1))
			.andExpect(jsonPath("$.day.shown.sections[1].citations[0].articleId").value(kept))
			.andExpect(jsonPath("$.day.shown.sections[1].citations[0].reason").value("MAJOR_STORYLINE"))
			.andExpect(jsonPath("$.day.shown.sections[1].citations[0].article.title").value("남은 기사 제목"))
			.andExpect(jsonPath("$.day.shown.sections[1].citations[0].article.url").value(url))
			.andExpect(jsonPath("$.day.shown.sections[1].citations[0].article.publishedAt")
				.value("2026-11-05T00:00:00Z"))
			.andExpect(jsonPath("$.day.shown.sections[1].citations[0].article.sources").isEmpty())
			.andExpect(jsonPath("$.day.shown.sections[2].kind").value("MORE_HEADLINES"))
			.andExpect(jsonPath("$.day.shown.sections[2].citations[0].note").value("한 줄 코멘트"))
			.andExpect(jsonPath("$.day.shown.sections[2].citations[0].reason").value("INTEREST"))
			.andExpect(jsonPath("$.day.latest.digestId").value(failed))
			.andExpect(jsonPath("$.day.latest.status").value("FAILED"))
			.andExpect(jsonPath("$.day.latest.failure").value("NO_CITATION"))
			.andExpect(jsonPath("$.day.latest.cause").value("OUTPUT"))
			.andExpect(jsonPath("$.day.usage.attempts").value(2))
			.andExpect(jsonPath("$.day.usage.inputTokens").value(150))
			.andExpect(jsonPath("$.day.usage.outputTokens").value(20))
			.andExpect(jsonPath("$.status.usableModel").value(true))
			.andExpect(jsonPath("$.status.inProgress").value(false))
			.andExpect(jsonPath("$.status.remainingToday").value(3))
			.andReturn()
			.getResponse()
			.getContentAsString();

		// 실패 메시지 · 요약문은 내지 않는다
		assertThat(body).doesNotContain("운영 로그용 실패 메시지").doesNotContain("errorMessage").doesNotContain("요약문 원문");
	}

	/**
	 * 수동 생성 상태의 시간대 · 해결되지 않은 마지막 실패. 오늘 일정 생성 시각의 값은 이 계층에 시계를 넣을 수 없어(시스템 시계) 고정 시계의
	 * 다이제스트 조회 시험이 보고, 여기서는 가장 늦은 날짜가 오늘보다 늦은 행으로 null 만 본다.
	 */
	@Test
	void theStatusCarriesTheTimezoneAndTheUnresolvedFailureUntilACoveringRow() throws Exception {
		long user = this.reader.id().value();
		this.rows.digest(user, "2099-01-01", "FAILED", null, null, "PROVIDER_UNAVAILABLE");

		this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.timezone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.status.scheduledAt").isEmpty())
			.andExpect(jsonPath("$.status.unresolvedFailure.digestDate").value("2099-01-01"))
			.andExpect(jsonPath("$.status.unresolvedFailure.failedAt").value("2026-11-05T15:05:00Z"));

		this.rows.digest(user, "2099-01-01", "READY", 100, 20, null);

		this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.unresolvedFailure").isEmpty());
	}

	@Test
	void aUserWithoutDigestsHasAnEmptyHomeAndADateWithoutDigestsIsNotFound() throws Exception {
		this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.day").isEmpty())
			.andExpect(jsonPath("$.status.targetDate").isNotEmpty())
			.andExpect(jsonPath("$.status.regenerates").value(false));
		this.mockMvc.perform(get("/api/digests/2026-11-05").with(this.reader)).andExpect(status().isNotFound());
	}

	@Test
	void malformedDatesAreBadRequests() throws Exception {
		// 연도는 네 자리다 — 부호 붙은 긴 연도(LocalDate 로는 읽힌다)는 DB 의 날짜 범위를 넘을 수 있다
		for (String date : new String[] { "2026-13-01", "20261105", "today", "+10000-01-01", "+100000000-01-01" }) {
			this.mockMvc.perform(get("/api/digests/{date}", date).with(this.reader))
				.andExpect(status().isBadRequest());
			this.mockMvc.perform(get("/api/digests").param("before", date).with(this.reader))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void theDateListComesInPagesOfThirtyLatestFirst() throws Exception {
		long user = this.reader.id().value();
		LocalDate first = LocalDate.parse("2026-10-01");
		for (int i = 0; i < 31; i++) {
			this.rows.digest(user, first.plusDays(i).toString(), "EMPTY", null, null, null);
		}
		this.rows.digest(user, "2026-10-31", "READY", 100, 20, null);

		this.mockMvc.perform(get("/api/digests").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dates.length()").value(30))
			.andExpect(jsonPath("$.dates[0].date").value("2026-10-31"))
			.andExpect(jsonPath("$.dates[0].state").value("CONTENT"))
			.andExpect(jsonPath("$.dates[1].date").value("2026-10-30"))
			.andExpect(jsonPath("$.dates[1].state").value("EMPTY"))
			.andExpect(jsonPath("$.dates[29].date").value("2026-10-02"))
			.andExpect(jsonPath("$.next").value("2026-10-02"));
		this.mockMvc.perform(get("/api/digests").param("before", "2026-10-02").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dates.length()").value(1))
			.andExpect(jsonPath("$.dates[0].date").value("2026-10-01"))
			.andExpect(jsonPath("$.next").isEmpty());
	}

	@Test
	void aRequestIsAcceptedForTheTargetDateTheHomeShowed() throws Exception {
		String home = this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String target = JsonPath.read(home, "$.status.targetDate");

		this.mockMvc.perform(post("/api/digests").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("REQUESTED"))
			.andExpect(jsonPath("$.digestDate").value(target))
			.andExpect(jsonPath("$.regenerated").value(false))
			.andExpect(jsonPath("$.resetsAt").isEmpty());
		// 받은 뒤 홈은 진행 중이다
		this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.day.latest.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.status.inProgress").value(true))
			.andExpect(jsonPath("$.status.remainingToday").value(2));
	}

	@Test
	void aRequestWhileOneIsInProgressIsRefused() throws Exception {
		this.rows.digest(this.reader.id().value(), "2026-11-05", "RUNNING", null, null, null);

		this.mockMvc.perform(post("/api/digests").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.digestDate").isEmpty())
			.andExpect(jsonPath("$.regenerated").isEmpty())
			.andExpect(jsonPath("$.resetsAt").isEmpty());
	}

	@Test
	void aFourthRequestOfTheDayIsRefusedUntilTheNextDay() throws Exception {
		long user = this.reader.id().value();
		for (int i = 0; i < 3; i++) {
			long row = this.rows.digest(user, "2026-11-05", "EMPTY", null, null, null);
			this.jdbc.sql("update digests set created_at = now() where id = :id").param("id", row).update();
		}

		this.mockMvc.perform(post("/api/digests").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("DAILY_LIMIT_REACHED"))
			.andExpect(jsonPath("$.resetsAt").isNotEmpty())
			.andExpect(jsonPath("$.digestDate").isEmpty());
		this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.remainingToday").value(0));
	}

	@Test
	void aRequestWithoutAUsableModelIsRefused() throws Exception {
		this.jdbc.sql("update llm_credentials set status = 'INVALID' where user_id = :user")
			.param("user", this.reader.id().value())
			.update();

		this.mockMvc.perform(post("/api/digests").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("NO_USABLE_MODEL"));
		this.mockMvc.perform(get("/api/digests/latest").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.usableModel").value(false));
		assertThat(this.rows.count(this.reader.id().value())).isZero();
	}

}
