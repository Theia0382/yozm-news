package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.FeedStatusEntry;
import net.yozmnews.user.domain.notice.NoticeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림 경로의 사용자 격리 — 사용자 A 의 세션으로 B 의 알림을 목록 · 수에서 볼 수 없고, B 의 알림 ID · 판으로 읽음을 보내도 B 의 알림은 그대로다.
 * 세션 없이 부르면 401 이다(docs/qa/test-strategy.md §13).
 * <p>
 * 지키는 규칙: user-R6 · user-R27
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public class NoticeScopeTests {

	private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

	private static final FeedStatusContent CONTENT = new FeedStatusContent(List.of(new FeedStatusEntry(new FeedId(11),
			"알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.DEGRADED, T, T, 1)));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	NoticeRepository notices;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void userASeesAndReadsOnlyTheirOwnNotices() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("notice-a@example.com", AccountRole.ADMIN);
		SignedInUsers.SignedInUser b = users.signIn("notice-b@example.com", AccountRole.ADMIN);
		long aNotice = this.notices.create(a.id(), CONTENT, T);
		long bNotice = this.notices.create(b.id(), CONTENT, T);

		this.mockMvc.perform(get("/api/notices").with(a))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"notices":[{"id":%d}],"nextBefore":null}""".formatted(aNotice), JsonCompareMode.LENIENT));
		this.mockMvc.perform(get("/api/notices").with(a).param("before", Long.toString(bNotice + 1)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"notices":[{"id":%d}],"nextBefore":null}""".formatted(aNotice), JsonCompareMode.LENIENT));
		this.mockMvc.perform(get("/api/notices/unread-count").with(a))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"count":1}""", JsonCompareMode.STRICT));
		this.mockMvc
			.perform(post("/api/notices/read").with(a).contentType(MediaType.APPLICATION_JSON).content("""
					{"notices":[{"id":%d,"revision":1}]}""".formatted(bNotice)))
			.andExpect(status().isNoContent());
		assertThat(this.notices.countUnread(b.id())).as("A 의 읽음 요청은 B 의 알림을 바꾸지 않는다").isOne();

		this.mockMvc
			.perform(post("/api/notices/read").with(a).contentType(MediaType.APPLICATION_JSON).content("""
					{"notices":[{"id":%d,"revision":1}]}""".formatted(aNotice)))
			.andExpect(status().isNoContent());
		assertThat(this.notices.countUnread(a.id())).isZero();
		assertThat(this.notices.countUnread(b.id())).isOne();
	}

	@Test
	void noticePathsNeedASession() throws Exception {
		Cookie csrf = new Cookie("XSRF-TOKEN", "notice-scope");

		this.mockMvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/notices/unread-count")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(post("/api/notices/read").cookie(csrf)
				.header("X-XSRF-TOKEN", "notice-scope")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"notices":[]}"""))
			.andExpect(status().isUnauthorized());
	}

}
