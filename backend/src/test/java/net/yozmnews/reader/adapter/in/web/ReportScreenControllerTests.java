package net.yozmnews.reader.adapter.in.web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.SignedInUsers;
import net.yozmnews.account.application.PasswordHashing;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.credential.PasswordCredentialRepository;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.reader.ReportRows;
import net.yozmnews.reader.UserStorylineSummaryRows;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 독자 화면의 변화 리포트 — 종류 하나의 목록(쪽 · 커서), 한 편(섹션 · 인용 · 숫자 · 숨긴 수 · 직전 리포트 · 사용량)과 스토리라인 · 사건의 표시
 * 이름, 틀린 요청의 400 · 없는 리포트의 404.
 * <p>
 * 실제 PostgreSQL 위에서 행을 SQL 로 넣고, 풀기 · 꼭대기 · 정리 · 대표 기사는 실제 공개 조회를 거친다. 기대값은 손으로 쓴 리터럴이다.
 * <p>
 * 지키는 규칙: docs/specs/reader.md reader-R15 · reader-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportScreenControllerTests {

	private static final Instant AT = Instant.parse("2026-11-20T00:00:00Z");

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

	private ReportRows reports;

	private UserStorylineSummaryRows summaries;

	private SignedInUsers.SignedInUser reader;

	@BeforeEach
	void signIn() throws Exception {
		this.rows = new ReaderRows(this.jdbc);
		this.reports = new ReportRows(this.jdbc);
		this.summaries = new UserStorylineSummaryRows(this.jdbc);
		clear();
		this.reader = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("report-screen@example.com");
		LlmKeyRows.give(this.jdbc, this.reader.id().value());
	}

	@AfterEach
	void clear() {
		this.reports.clear();
		this.rows.clear();
		UserTables.deleteAll(this.jdbc);
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void listsOneKindLatestFirstWithThePeriodDaysAndAPageCursor() throws Exception {
		long user = this.reader.id().value();
		long[] weeks = new long[ReportScreenController.REPORTS_PAGE_SIZE + 1];
		// 같은 사용자 · 종류 · 기간은 하나라 앞선 주들은 한 주씩 앞선 기간이다 — 가장 늦은 실패만 기본 기간
		for (int i = 0; i < weeks.length; i++) {
			Duration earlier = Duration.ofDays(7L * (weeks.length - i));
			weeks[i] = this.reports.report(user, "WEEK", "EMPTY", ReportRows.WEEK_START.minus(earlier),
					ReportRows.WEEK_END.minus(earlier), null, null);
		}
		long failed = this.reports.report(user, "FAILED", null);
		this.reports.report(user, "MONTH", "READY", Instant.parse("2026-09-30T15:00:00Z"),
				Instant.parse("2026-10-31T15:00:00Z"), null, null);

		this.mockMvc.perform(get("/api/reports").param("kind", "WEEK").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports.length()").value(20))
			.andExpect(jsonPath("$.reports[0].id").value(failed))
			.andExpect(jsonPath("$.reports[0].kind").value("WEEK"))
			.andExpect(jsonPath("$.reports[0].firstDay").value("2026-11-16"))
			.andExpect(jsonPath("$.reports[0].lastDay").value("2026-11-22"))
			.andExpect(jsonPath("$.reports[0].status").value("FAILED"))
			.andExpect(jsonPath("$.reports[0].failure").value("QUOTA_EXHAUSTED"))
			.andExpect(jsonPath("$.reports[0].completedAt").value("2026-11-22T15:01:00Z"))
			.andExpect(jsonPath("$.reports[0].message").doesNotExist())
			.andExpect(jsonPath("$.next").value(weeks[2]));
		this.mockMvc.perform(get("/api/reports").param("kind", "WEEK").param("before", String.valueOf(weeks[2]))
			.with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports.length()").value(2))
			.andExpect(jsonPath("$.reports[0].id").value(weeks[1]))
			.andExpect(jsonPath("$.reports[1].id").value(weeks[0]))
			.andExpect(jsonPath("$.next").isEmpty());
		this.mockMvc.perform(get("/api/reports").param("kind", "MONTH").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports.length()").value(1))
			.andExpect(jsonPath("$.reports[0].firstDay").value("2026-10-01"))
			.andExpect(jsonPath("$.reports[0].lastDay").value("2026-10-31"));
	}

	@Test
	void refusesAMissingOrUnknownKindAndACursorBelowOne() throws Exception {
		this.mockMvc.perform(get("/api/reports").with(this.reader)).andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/reports").param("kind", "YEAR").with(this.reader))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/reports").param("kind", "WEEK").param("before", "0").with(this.reader))
			.andExpect(status().isBadRequest());
		// 대조 — 맞는 요청은 200
		this.mockMvc.perform(get("/api/reports").param("kind", "WEEK").param("before", "1").with(this.reader))
			.andExpect(status().isOk());
	}

	@Test
	void anIdBelowOneOrOfNoReportOfTheUserIsNotFound() throws Exception {
		long others = this.reports.report(this.reader.id().value() + 1_000, "READY", null);

		this.mockMvc.perform(get("/api/reports/{id}", 0).with(this.reader)).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/reports/{id}", others + 1_000).with(this.reader))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/reports/{id}", others).with(this.reader)).andExpect(status().isNotFound());
	}

	@Test
	void aReadyReportNamesItsStorylinesAndEventsAndShowsTheNumbersOfEachSection() throws Exception {
		long user = this.reader.id().value();
		// 정리된 스토리라인 — 사용자 스토리라인의 추적 대상이 그 스토리라인
		long summarized = storyline("정리된 흐름의 대표");
		long ofSummarized = this.summaries.userStoryline(user, summarized);
		this.summaries.summarize(ofSummarized, "정리 하나", AT);
		// 합쳐진 스토리라인 — 남는 스토리라인의 정리로 잇는다
		long merged = storyline("합쳐진 흐름의 대표");
		long survivor = storyline("남는 흐름의 대표");
		this.rows.merge(merged, survivor);
		long ofSurvivor = this.summaries.userStoryline(user, survivor);
		this.summaries.summarize(ofSurvivor, "남는 정리", AT);
		// 생성 뒤 다른 스토리라인의 하위로 들어간 헤드 — 지금 꼭대기의 정리로 잇는다
		long parent = storyline("상위 흐름의 대표");
		long child = storyline("하위 흐름의 대표");
		this.rows.relate("CONTAINS", parent, child);
		long ofParent = this.summaries.userStoryline(user, parent);
		this.summaries.summarize(ofParent, "상위 정리", AT);
		// 정리가 없는 스토리라인
		long bare = storyline("정리 없는 흐름의 대표");
		// 숫자 줄에만 있고 인용되지 않은 스토리라인 — 글이 말하지 않아도 표에는 이름이 붙는다
		long uncited = storyline("인용 없는 흐름의 대표");
		// 사건 인용
		long article = this.rows.article("사건의 대표 기사");
		long event = this.rows.event(1, "2026-11-17T00:00:00Z", "2026-11-17T00:00:00Z");
		this.rows.assign(article, event, "2026-11-17T00:00:00Z");
		// 대표 기사가 없는 사건 — 인용은 남고 기사는 없다
		long withoutArticle = this.rows.event(1, "2026-11-17T00:00:00Z", "2026-11-17T00:00:00Z");

		long previous =this.reports.report(user, "WEEK", "READY", Instant.parse("2026-11-08T15:00:00Z"),
				ReportRows.WEEK_START, null, null);
		long id = this.reports.report(user, "WEEK", "READY", ReportRows.WEEK_START, ReportRows.WEEK_END, """
				{"version":2,"tagSet":4,
				 "period":{"kind":"WEEK","start":"2026-11-15T15:00:00Z","end":"2026-11-22T15:00:00Z"},
				 "storylines":{"basis":"PUBLISHED","userStorylines":[
				   {"head":%d,"tops":[%d],"articles":8,"previous":2,"trend":"ROSE","trackedInPeriod":false},
				   {"head":%d,"tops":[%d],"articles":5,"previous":null,"trend":"NO_COMPARISON","trackedInPeriod":true},
				   {"head":%d,"tops":[%d],"articles":3,"previous":3,"trend":"STEADY","trackedInPeriod":false}]},
				 "coverage":{"basis":"FIRST_EVALUATED","topics":[
				   {"key":"economy.finance","name":"금융","articles":40,"previous":12,"trend":"ROSE"}]},
				 "interests":{"topics":[{"key":"economy.finance","name":"금융","start":0.42,"end":null,"shift":"LEFT"}],
				   "userStorylines":[{"head":%d,"start":0.2,"end":0.6,"shift":"ENTERED"}]}}"""
			.formatted(summarized, summarized, merged, merged, uncited, uncited, child), previous);
		this.reports.section(id, "OVERVIEW", 0, null, "개요");
		long storylineSection = this.reports.section(id, "STORYLINES", 1, "흐름", "흐름 본문");
		this.reports.item(storylineSection, 0, summarized, null);
		this.reports.item(storylineSection, 1, merged, null);
		this.reports.item(storylineSection, 2, null, event);
		this.reports.item(storylineSection, 3, null, withoutArticle);
		this.reports.section(id, "COVERAGE", 2, "보도량", "보도량 본문");
		long interests = this.reports.section(id, "INTERESTS", 3, "관심", "관심 본문");
		this.reports.item(interests, 0, child, null);
		this.reports.item(interests, 1, bare, null);

		this.mockMvc.perform(get("/api/reports/{id}", id).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.report.id").value(id))
			.andExpect(jsonPath("$.report.firstDay").value("2026-11-16"))
			.andExpect(jsonPath("$.report.lastDay").value("2026-11-22"))
			.andExpect(jsonPath("$.previous.id").value(previous))
			.andExpect(jsonPath("$.previous.firstDay").value("2026-11-09"))
			.andExpect(jsonPath("$.hiddenSections").value(0))
			.andExpect(jsonPath("$.usage.provider").value("openai"))
			.andExpect(jsonPath("$.usage.model").value("gpt-5.5-mini"))
			.andExpect(jsonPath("$.usage.inputTokens").value(120))
			.andExpect(jsonPath("$.usage.outputTokens").value(45))
			.andExpect(jsonPath("$.sections.length()").value(4))
			.andExpect(jsonPath("$.sections[0].kind").value("OVERVIEW"))
			.andExpect(jsonPath("$.sections[0].userStorylines").isEmpty())
			// 사용자 스토리라인 섹션 — 정리된 스토리라인, 합쳐진 스토리라인은 남는 스토리라인, 사건은 대표 기사
			.andExpect(jsonPath("$.sections[1].citations[0].kind").value("STORYLINE"))
			.andExpect(jsonPath("$.sections[1].citations[0].storyline.storylineId").value(summarized))
			// 화면용 API 의 칸 이름은 storyline · userStorylines 다 — 옛 이름 flow · flows 는 없다
			.andExpect(jsonPath("$.sections[1].citations[0].flow").doesNotExist())
			.andExpect(jsonPath("$.sections[1].flows").doesNotExist())
			.andExpect(jsonPath("$.sections[1].citations[0].storyline.title.title").value("정리된 흐름의 대표"))
			.andExpect(jsonPath("$.sections[1].citations[0].storyline.userStorylines[0].id").value(ofSummarized))
			.andExpect(jsonPath("$.sections[1].citations[0].storyline.userStorylines[0].title").value("정리 하나 제목"))
			.andExpect(jsonPath("$.sections[1].citations[1].storyline.storylineId").value(survivor))
			.andExpect(jsonPath("$.sections[1].citations[1].storyline.title.title").value("남는 흐름의 대표"))
			.andExpect(jsonPath("$.sections[1].citations[1].storyline.userStorylines[0].title").value("남는 정리 제목"))
			.andExpect(jsonPath("$.sections[1].citations[2].kind").value("EVENT"))
			.andExpect(jsonPath("$.sections[1].citations[2].eventId").value(event))
			.andExpect(jsonPath("$.sections[1].citations[2].article.title").value("사건의 대표 기사"))
			.andExpect(jsonPath("$.sections[1].citations[2].article.url").value(startsWith("https://event.example/")))
			.andExpect(jsonPath("$.sections[1].citations[2].storyline").isEmpty())
			.andExpect(jsonPath("$.sections[1].citations.length()").value(4))
			.andExpect(jsonPath("$.sections[1].citations[3].kind").value("EVENT"))
			.andExpect(jsonPath("$.sections[1].citations[3].eventId").value(withoutArticle))
			.andExpect(jsonPath("$.sections[1].citations[3].article").isEmpty())
			// 사용자 스토리라인 섹션의 숫자 — 줄의 헤드는 지금 스토리라인으로 풀려 이름이 붙는다
			.andExpect(jsonPath("$.sections[1].userStorylines.length()").value(3))
			.andExpect(jsonPath("$.sections[1].userStorylines[0].storyline.userStorylines[0].title").value("정리 하나 제목"))
			.andExpect(jsonPath("$.sections[1].userStorylines[0].articles").value(8))
			.andExpect(jsonPath("$.sections[1].userStorylines[0].previous").value(2))
			.andExpect(jsonPath("$.sections[1].userStorylines[0].trend").value("ROSE"))
			.andExpect(jsonPath("$.sections[1].userStorylines[0].trackedInPeriod").value(false))
			.andExpect(jsonPath("$.sections[1].userStorylines[1].storyline.storylineId").value(survivor))
			.andExpect(jsonPath("$.sections[1].userStorylines[1].previous").isEmpty())
			.andExpect(jsonPath("$.sections[1].userStorylines[1].trend").value("NO_COMPARISON"))
			.andExpect(jsonPath("$.sections[1].userStorylines[2].storyline.storylineId").value(uncited))
			.andExpect(jsonPath("$.sections[1].userStorylines[2].storyline.title.title").value("인용 없는 흐름의 대표"))
			.andExpect(jsonPath("$.sections[1].userStorylines[2].storyline.userStorylines.length()").value(0))
			.andExpect(jsonPath("$.sections[1].userStorylines[2].articles").value(3))
			.andExpect(jsonPath("$.sections[1].topics").isEmpty())
			// 보도량 섹션
			.andExpect(jsonPath("$.sections[2].citations.length()").value(0))
			.andExpect(jsonPath("$.sections[2].topics[0].name").value("금융"))
			.andExpect(jsonPath("$.sections[2].topics[0].articles").value(40))
			.andExpect(jsonPath("$.sections[2].topics[0].previous").value(12))
			.andExpect(jsonPath("$.sections[2].topics[0].trend").value("ROSE"))
			// 관심 섹션 — 하위로 들어간 스토리라인은 지금 꼭대기의 정리, 정리 없는 스토리라인은 빈 목록과 대표 기사
			.andExpect(jsonPath("$.sections[3].citations[0].storyline.storylineId").value(child))
			.andExpect(jsonPath("$.sections[3].citations[0].storyline.title.title").value("하위 흐름의 대표"))
			.andExpect(jsonPath("$.sections[3].citations[0].storyline.userStorylines[0].id").value(ofParent))
			.andExpect(jsonPath("$.sections[3].citations[0].storyline.userStorylines[0].title").value("상위 정리 제목"))
			.andExpect(jsonPath("$.sections[3].citations[1].storyline.storylineId").value(bare))
			.andExpect(jsonPath("$.sections[3].citations[1].storyline.title.title").value("정리 없는 흐름의 대표"))
			.andExpect(jsonPath("$.sections[3].citations[1].storyline.userStorylines.length()").value(0))
			.andExpect(jsonPath("$.sections[3].interests.topics[0].name").value("금융"))
			.andExpect(jsonPath("$.sections[3].interests.topics[0].start").value(0.42))
			.andExpect(jsonPath("$.sections[3].interests.topics[0].end").isEmpty())
			.andExpect(jsonPath("$.sections[3].interests.topics[0].shift").value("LEFT"))
			.andExpect(jsonPath("$.sections[3].interests.userStorylines[0].storyline.userStorylines[0].title").value("상위 정리 제목"))
			.andExpect(jsonPath("$.sections[3].interests.userStorylines[0].end").value(0.6))
			.andExpect(jsonPath("$.sections[3].interests.userStorylines[0].shift").value("ENTERED"))
			.andExpect(jsonPath("$.sections[3].interests.flows").doesNotExist());
	}

	@Test
	void aReportThatLostCitationsCountsItsHiddenSectionsAndOneNotStartedHasNoUsage() throws Exception {
		long user = this.reader.id().value();
		long partly = this.reports.report(user, "READY", null);
		this.reports.section(partly, "OVERVIEW", 0, null, "개요");
		this.reports.item(this.reports.section(partly, "STORYLINES", 1, "흐름", "본문"), 0, 999_991L, null);
		this.reports.section(partly, "COVERAGE", 2, "보도량", "본문");
		long scheduled = this.reports.report(user, "WEEK", "SCHEDULED", ReportRows.WEEK_END,
				ReportRows.WEEK_END.plus(Duration.ofDays(7)), null, null);

		this.mockMvc.perform(get("/api/reports/{id}", partly).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hiddenSections").value(1))
			.andExpect(jsonPath("$.sections.length()").value(2))
			.andExpect(jsonPath("$.sections[1].kind").value("COVERAGE"))
			.andExpect(jsonPath("$.previous").isEmpty());
		this.mockMvc.perform(get("/api/reports/{id}", scheduled).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.report.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.usage").isEmpty())
			.andExpect(jsonPath("$.hiddenSections").value(0))
			.andExpect(jsonPath("$.sections.length()").value(0));
	}

	@Test
	void aFailedReportIsRegeneratedAndTheResultSaysWhyNotOtherwise() throws Exception {
		long user = this.reader.id().value();
		long failed = this.reports.report(user, "FAILED", null);
		long ready = this.reports.report(user, "WEEK", "READY", ReportRows.WEEK_END,
				ReportRows.WEEK_END.plus(Duration.ofDays(7)), null, null);
		long spent = this.reports.report(user, "WEEK", "FAILED", ReportRows.WEEK_START.minus(Duration.ofDays(7)),
				ReportRows.WEEK_START, null, null);
		this.jdbc.sql("update reports set regenerations = 3 where id = :id").param("id", spent).update();

		// 한 편은 실패일 때만 남은 다시 만들기 수를 싣는다
		this.mockMvc.perform(get("/api/reports/{id}", failed).with(this.reader))
			.andExpect(jsonPath("$.regenerationsLeft").value(3));
		this.mockMvc.perform(get("/api/reports/{id}", spent).with(this.reader))
			.andExpect(jsonPath("$.regenerationsLeft").value(0));
		this.mockMvc.perform(get("/api/reports/{id}", ready).with(this.reader))
			.andExpect(jsonPath("$.regenerationsLeft").isEmpty());

		this.mockMvc.perform(post("/api/reports/{id}/regeneration", ready).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"NOT_FAILED","status":"READY"}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(post("/api/reports/{id}/regeneration", spent).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"LIMIT_REACHED","status":null}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(post("/api/reports/{id}/regeneration", failed).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REQUESTED","status":null}""", JsonCompareMode.STRICT));
		this.mockMvc.perform(get("/api/reports/{id}", failed).with(this.reader))
			.andExpect(jsonPath("$.report.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.regenerationsLeft").isEmpty());
		// 없는 리포트 · 1 보다 작은 ID 는 404
		this.mockMvc.perform(post("/api/reports/{id}/regeneration", Long.MAX_VALUE).with(this.reader))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(post("/api/reports/{id}/regeneration", 0).with(this.reader))
			.andExpect(status().isNotFound());
	}

	@Test
	void theConditionsAreTheGenerationThresholds() throws Exception {
		this.mockMvc.perform(get("/api/reports/conditions").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"minDigestDays":3,"minWeeklyReports":2}""", JsonCompareMode.STRICT));
	}

	@Test
	void aUserWithoutAUsableModelIsToldSo() throws Exception {
		long user = this.reader.id().value();
		long failed = this.reports.report(user, "FAILED", null);
		// 키는 있지만 무효 — 관문은 지나고 쓸 모델은 없다
		this.jdbc.sql("update llm_credentials set status = 'INVALID' where user_id = :user").param("user", user).update();

		this.mockMvc.perform(post("/api/reports/{id}/regeneration", failed).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"NO_USABLE_MODEL","status":null}""", JsonCompareMode.STRICT));
	}

	/** 스토리라인 하나 — 대표 사건과 그 사건의 기사 하나(대표 기사) */
	private long storyline(String title) {
		long article = this.rows.article(title);
		long event = this.rows.event(1, "2026-11-17T00:00:00Z", "2026-11-17T00:00:00Z");
		this.rows.assign(article, event, "2026-11-17T00:00:00Z");
		long storyline = this.rows.storyline("2026-11-17T00:00:00Z", "2026-11-17T00:00:00Z", event);
		this.rows.member(storyline, event);
		return storyline;
	}

}
