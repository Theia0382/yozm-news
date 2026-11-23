package net.yozmnews.reader.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import net.yozmnews.LlmKeyRows;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.reader.DigestScreenRows;
import net.yozmnews.reader.UserStorylineSummaryRows;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.application.PasswordHashing;
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
 * 독자 화면의 정리된 사용자 스토리라인 — 목록(정리가 있는 것만 · 마지막 정리 순), 하나(흐름 전체의 글 · 꼭대기마다의 글과 상태 · 사용량), 그 흐름에
 * 묶인 지난 다이제스트 항목(쪽 · 커서), 없는 ID 의 404 와 틀린 커서의 400.
 * <p>
 * 실제 PostgreSQL 위에서 행을 SQL 로 넣는다 — 정리 작업 · 생성기는 돌지 않는다.
 * <p>
 * 지키는 규칙: docs/specs/reader.md 의 사용자 스토리라인 화면
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class UserStorylineScreenControllerTests {

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

	private UserStorylineSummaryRows rows;

	private DigestScreenRows digests;

	private SignedInUsers.SignedInUser reader;

	@BeforeEach
	void signIn() throws Exception {
		this.rows = new UserStorylineSummaryRows(this.jdbc);
		this.digests = new DigestScreenRows(this.jdbc);
		clear();
		this.reader = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("user-storyline-screen@example.com");
		LlmKeyRows.give(this.jdbc, this.reader.id().value());
	}

	@AfterEach
	void clear() {
		this.digests.clear();
		this.jdbc.sql("delete from storylines").update();
		this.jdbc.sql("delete from users").update();
		this.jdbc.sql("delete from login_failures").update();
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void theListHoldsOnlySummarizedUserStorylinesLatestSummaryFirst() throws Exception {
		long user = this.reader.id().value();
		long older = this.rows.userStoryline(user, 12, 11);
		long newer = this.rows.userStoryline(user, 21);
		this.rows.userStoryline(user, 31);
		this.rows.summarize(older, "앞", AT);
		this.rows.summarize(newer, "뒤", AT.plusSeconds(60));
		this.rows.track(user, older);

		this.mockMvc.perform(get("/api/user-storylines").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"userStorylines": [
					  {"id": %d, "title": "뒤 제목", "summary": "뒤 요약", "recent": "뒤 최근", "tracked": false,
					   "storylineId": 21, "topCount": 1, "updatedAt": "2026-11-20T00:01:00Z"},
					  {"id": %d, "title": "앞 제목", "summary": "앞 요약", "recent": "앞 최근", "tracked": true,
					   "storylineId": 11, "topCount": 2, "updatedAt": "2026-11-20T00:00:00Z"}
					]}""".formatted(newer, older), JsonCompareMode.STRICT));
	}

	@Test
	void theTrackButtonOfACardAndOfTheDetailIsTheStoredHead() throws Exception {
		long user = this.reader.id().value();
		// 추적 대상 11 · 12 에 저장 헤드 12 — 가장 작은 꼭대기(11)가 아니라 헤드가 손잡이다
		long id = this.rows.userStoryline(user, 11, 12);
		this.rows.summarize(id, "갈래", AT);
		this.jdbc.sql("update user_storylines set head = 12 where id = :id").param("id", id).update();

		this.mockMvc.perform(get("/api/user-storylines").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userStorylines[0].storylineId").value(12));
		this.mockMvc.perform(get("/api/user-storylines/{id}", id).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storylineId").value(12));
	}

	@Test
	void aUserWithoutSummariesHasAnEmptyList() throws Exception {
		this.rows.userStoryline(this.reader.id().value(), 11);

		this.mockMvc.perform(get("/api/user-storylines").with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"userStorylines\": []}", JsonCompareMode.STRICT));
	}

	@Test
	void oneUserStorylineCarriesItsTextsItsTopsWithTheirStateAndTheUsage() throws Exception {
		long user = this.reader.id().value();
		long written = UserStorylineFixtures.storyline(this.jdbc, AT);
		long unwritten = UserStorylineFixtures.storyline(this.jdbc, AT);
		long gone = UserStorylineFixtures.storyline(this.jdbc, AT);
		long id = this.rows.userStoryline(user, written, unwritten, gone);
		this.rows.summarize(id, "전체", AT);
		this.rows.top(id, written, "가");
		this.rows.top(id, unwritten, "");
		this.rows.top(id, gone, "다");
		this.rows.track(user, id);
		this.jdbc.sql("delete from storylines where id = :id").param("id", gone).update();

		this.mockMvc.perform(get("/api/user-storylines/{id}", id).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.title").value("전체 제목"))
			.andExpect(jsonPath("$.summary").value("전체 요약"))
			.andExpect(jsonPath("$.recent").value("전체 최근"))
			.andExpect(jsonPath("$.tracked").value(true))
			.andExpect(jsonPath("$.storylineId").value(written))
			.andExpect(jsonPath("$.createdAt").value("2026-11-01T00:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-11-20T00:00:00Z"))
			// 그새 없어진 스토리라인의 꼭대기는 빠지고, 글 없는 꼭대기는 글 없이 남는다
			.andExpect(jsonPath("$.tops.length()").value(2))
			.andExpect(jsonPath("$.tops[0].storylineId").value(written))
			.andExpect(jsonPath("$.tops[0].title").value("가 제목"))
			.andExpect(jsonPath("$.tops[0].summary").value("가 요약"))
			.andExpect(jsonPath("$.tops[0].recent").value("가 최근"))
			.andExpect(jsonPath("$.tops[0].state").isString())
			.andExpect(jsonPath("$.tops[1].storylineId").value(unwritten))
			.andExpect(jsonPath("$.tops[1].title").isEmpty())
			.andExpect(jsonPath("$.tops[1].summary").isEmpty())
			.andExpect(jsonPath("$.tops[1].recent").isEmpty())
			.andExpect(content().json("""
					{"usage": {"provider": "anthropic", "model": "claude-sonnet-5-5", "inputTokens": 1200,
					           "outputTokens": 300, "calls": 2, "totalInputTokens": 1500, "totalOutputTokens": 340}}"""));
	}

	@Test
	void anUnknownOrUnsummarizedUserStorylineIsNotFound() throws Exception {
		long unsummarized = this.rows.userStoryline(this.reader.id().value(), 11);

		for (String path : new String[] { "/api/user-storylines/" + unsummarized, "/api/user-storylines/999999999",
				"/api/user-storylines/0", "/api/user-storylines/" + unsummarized + "/items",
				"/api/user-storylines/0/items" }) {
			this.mockMvc.perform(get(path).with(this.reader)).andExpect(status().isNotFound());
		}
	}

	@Test
	void theItemsOfAFlowComeInPagesOfTwentyLatestFirst() throws Exception {
		long user = this.reader.id().value();
		long flow = UserStorylineFixtures.storyline(this.jdbc, AT);
		long id = this.rows.userStoryline(user, flow);
		this.rows.summarize(id, "전체", AT);
		long earlier = this.digests.digest(user, "2026-11-04", "READY", 1, 1, null);
		item(earlier, "TOP_STORY", 1, "4일 주요", flow);
		long later = this.digests.digest(user, "2026-11-05", "READY", 1, 1, null);
		for (int position = 1; position <= 20; position++) {
			item(later, "HEADLINE", position, "5일 " + position, flow);
		}
		long other = UserStorylineFixtures.storyline(this.jdbc, AT);
		item(later, "HEADLINE", 21, "다른 흐름", other);

		this.mockMvc.perform(get("/api/user-storylines/{id}/items", id).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(20))
			.andExpect(jsonPath("$.items[0].date").value("2026-11-05"))
			.andExpect(jsonPath("$.items[0].kind").value("HEADLINE"))
			.andExpect(jsonPath("$.items[0].title").value("5일 20"))
			.andExpect(jsonPath("$.items[19].title").value("5일 1"))
			.andExpect(jsonPath("$.next.beforeDate").value("2026-11-05"))
			.andExpect(jsonPath("$.next.beforePosition").value(1));
		this.mockMvc
			.perform(get("/api/user-storylines/{id}/items", id).param("beforeDate", "2026-11-05")
				.param("beforePosition", "1")
				.with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"items": [{"date": "2026-11-04", "kind": "TOP_STORY", "title": "4일 주요"}], "next": null}""", JsonCompareMode.STRICT));
	}

	@Test
	void aFlowWithoutItemsHasAnEmptyPage() throws Exception {
		long id = this.rows.userStoryline(this.reader.id().value(), 11);
		this.rows.summarize(id, "전체", AT);

		this.mockMvc.perform(get("/api/user-storylines/{id}/items", id).with(this.reader))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"items\": [], \"next\": null}", JsonCompareMode.STRICT));
	}

	@Test
	void aHalfCursorAMalformedDateOrANegativePositionIsABadRequest() throws Exception {
		long id = this.rows.userStoryline(this.reader.id().value(), 11);
		this.rows.summarize(id, "전체", AT);

		this.mockMvc.perform(get("/api/user-storylines/{id}/items", id).param("beforeDate", "2026-11-05").with(this.reader))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/user-storylines/{id}/items", id).param("beforePosition", "1").with(this.reader))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/api/user-storylines/{id}/items", id).param("beforeDate", "11/05")
				.param("beforePosition", "1")
				.with(this.reader))
			.andExpect(status().isBadRequest());
		this.mockMvc
			.perform(get("/api/user-storylines/{id}/items", id).param("beforeDate", "2026-11-05")
				.param("beforePosition", "-1")
				.with(this.reader))
			.andExpect(status().isBadRequest());
	}

	/** 그 흐름이 묶은 항목 섹션 하나 */
	private void item(long digest, String kind, int position, String title, long flow) {
		long section = this.digests.section(digest, kind, position, title, null);
		this.digests.sectionEvent(section, 0, 1_000 + position, "SUBSCRIBED");
		this.jdbc.sql("update digest_sections set storyline_id = :flow where id = :id")
			.param("flow", flow)
			.param("id", section)
			.update();
	}

}
