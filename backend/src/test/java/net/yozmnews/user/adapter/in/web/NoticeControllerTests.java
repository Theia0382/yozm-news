package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.servlet.http.Cookie;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventArticles;
import net.yozmnews.llm.Provider;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.tag.TagKind;
import net.yozmnews.user.PasswordChangeMethod;
import net.yozmnews.user.SignedInUsers;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import net.yozmnews.user.domain.notice.FeedStatusContent;
import net.yozmnews.user.domain.notice.FeedStatusEntry;
import net.yozmnews.user.domain.notice.KeyInvalidatedContent;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.PasswordChangedContent;
import net.yozmnews.user.domain.notice.PreferencesAdjustedContent;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.SeenNotice;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import net.yozmnews.user.domain.preference.Adjustment;
import net.yozmnews.user.domain.preference.AdjustmentKind;
import net.yozmnews.user.domain.preference.PreferenceMode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 알림함 API — 목록은 최근부터 50개씩이고 다음 쪽 커서를 준다. 응답은 손으로 쓴 JSON 과 STRICT 로 견준다(피드 식별 · 상태 · 시각 · 횟수 밖의
 * 키가 없다). 읽음은 받은 판이 그대로인 알림만 바꾸고 결과와 무관하게 204 다. 질의 · 본문의 값이 틀리면 400, 요청 보호 토큰이 없으면 403 이다.
 * <p>
 * 지키는 규칙: user-R21 · user-R25 · user-R27 · user-R36 · user-R54
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NoticeControllerTests {

	private static final Instant T1 = Instant.parse("2026-11-13T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-13T01:00:00Z");

	private static final FeedStatusContent FIRST = new FeedStatusContent(List.of(new FeedStatusEntry(new FeedId(11),
			"알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.DEGRADED, T1, T1, 1)));

	private static final FeedStatusContent SECOND = new FeedStatusContent(
			List.of(new FeedStatusEntry(new FeedId(11), "알림일보", "정치", FeedStatus.ACTIVE, FeedStatus.ACTIVE, T1, T2, 2),
					new FeedStatusEntry(new FeedId(12), null, null, FeedStatus.ACTIVE, FeedStatus.STALE, T2, T2, 1)));

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

	private SignedInUsers.SignedInUser admin;

	@BeforeEach
	void signIn() throws Exception {
		this.jdbc.sql("delete from users").update();
		this.admin = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing)
			.signIn("notices@example.com", AccountRole.ADMIN);
	}

	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void listsNoticesNewestFirstWithTheirRevisionAndReadFlag() throws Exception {
		long older = this.notices.create(this.admin.id(), FIRST, T1);
		this.notices.markRead(this.admin.id(), List.of(new SeenNotice(older, 1)), T2);
		long newer = this.notices.create(this.admin.id(), FIRST, T1);
		this.notices.revise(newer, SECOND, T2);

		this.mockMvc.perform(get("/api/notices").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"notices":[
					  {"id":%d,"kind":"FEED_STATUS","revision":2,"createdAt":"2026-11-13T00:00:00Z",
					   "updatedAt":"2026-11-13T01:00:00Z","read":false,"digestFailed":null,
					   "feedStatus":{"feeds":[
					     {"feedId":11,"sourceName":"알림일보","section":"정치","firstFrom":"ACTIVE","lastTo":"ACTIVE",
					      "firstAt":"2026-11-13T00:00:00Z","lastAt":"2026-11-13T01:00:00Z","changes":2},
					     {"feedId":12,"sourceName":null,"section":null,"firstFrom":"ACTIVE","lastTo":"STALE",
					      "firstAt":"2026-11-13T01:00:00Z","lastAt":"2026-11-13T01:00:00Z","changes":1}]},
					   "passwordChanged":null,"keyInvalidated":null,"preferencesAdjusted":null,"storylineSubscriptionsRemoved":null},
					  {"id":%d,"kind":"FEED_STATUS","revision":1,"createdAt":"2026-11-13T00:00:00Z",
					   "updatedAt":"2026-11-13T00:00:00Z","read":true,"digestFailed":null,
					   "feedStatus":{"feeds":[
					     {"feedId":11,"sourceName":"알림일보","section":"정치","firstFrom":"ACTIVE","lastTo":"DEGRADED",
					      "firstAt":"2026-11-13T00:00:00Z","lastAt":"2026-11-13T00:00:00Z","changes":1}]},
					   "passwordChanged":null,"keyInvalidated":null,"preferencesAdjusted":null,"storylineSubscriptionsRemoved":null}],
					 "nextBefore":null}
					""".formatted(newer, older), JsonCompareMode.STRICT));
		this.mockMvc.perform(get("/api/notices/unread-count").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"count":1}""", JsonCompareMode.STRICT));
	}

	@Test
	void passwordNoticeCarriesItsMethodAndTime() throws Exception {
		long id = this.notices.create(this.admin.id(),
				new PasswordChangedContent(PasswordChangeMethod.RECOVERY, T1), T1);

		this.mockMvc.perform(get("/api/notices").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"notices":[
					  {"id":%d,"kind":"PASSWORD_CHANGED","revision":1,"createdAt":"2026-11-13T00:00:00Z",
					   "updatedAt":"2026-11-13T00:00:00Z","read":false,"digestFailed":null,"feedStatus":null,
					   "passwordChanged":{"method":"RECOVERY","changedAt":"2026-11-13T00:00:00Z"},
					   "keyInvalidated":null,"preferencesAdjusted":null,"storylineSubscriptionsRemoved":null}],
					 "nextBefore":null}
					""".formatted(id), JsonCompareMode.STRICT));
	}

	@Test
	void keyNoticeCarriesItsProviderAndTimeButNotTheKeyId() throws Exception {
		assertThat(this.notices.createOnce(this.admin.id(),
				new KeyInvalidatedContent(Provider.ANTHROPIC, 4_107, Instant.parse("2026-11-13T00:30:00.123456Z")), T1))
			.isTrue();
		long id = this.notices.page(this.admin.id(), null, 1).getFirst().id();

		this.mockMvc.perform(get("/api/notices").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"notices":[
					  {"id":%d,"kind":"KEY_INVALIDATED","revision":1,"createdAt":"2026-11-13T00:00:00Z",
					   "updatedAt":"2026-11-13T00:00:00Z","read":false,"digestFailed":null,"feedStatus":null,"passwordChanged":null,
					   "keyInvalidated":{"provider":"ANTHROPIC","invalidatedAt":"2026-11-13T00:30:00.123456Z"},
					   "preferencesAdjusted":null,"storylineSubscriptionsRemoved":null}],
					 "nextBefore":null}
					""".formatted(id), JsonCompareMode.STRICT));
		this.mockMvc.perform(get("/api/notices/unread-count").with(this.admin))
			.andExpect(content().json("""
					{"count":1}""", JsonCompareMode.STRICT));
	}

	@Test
	void preferencesNoticeCarriesTheVersionNumberAndItsAdjustments() throws Exception {
		long id = this.notices.create(this.admin.id(), new PreferencesAdjustedContent(4,
				List.of(new Adjustment(TagKind.REGION, "china", "중국", PreferenceMode.EXCLUDE, AdjustmentKind.RELEASED_REMOVED),
						new Adjustment(TagKind.FORMAT, "listicle", "목록형", PreferenceMode.EXCLUDE,
								AdjustmentKind.ADDED_DEFAULT_EXCLUDED))),
				T1);

		this.mockMvc.perform(get("/api/notices").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"notices":[
					  {"id":%d,"kind":"PREFERENCES_ADJUSTED","revision":1,"createdAt":"2026-11-13T00:00:00Z",
					   "updatedAt":"2026-11-13T00:00:00Z","read":false,"digestFailed":null,"feedStatus":null,"passwordChanged":null,
					   "keyInvalidated":null,
					   "preferencesAdjusted":{"tagSetNumber":4,"adjustments":[
					     {"kind":"REGION","key":"china","name":"중국","mode":"EXCLUDE","change":"RELEASED_REMOVED"},
					     {"kind":"FORMAT","key":"listicle","name":"목록형","mode":"EXCLUDE","change":"ADDED_DEFAULT_EXCLUDED"}]},
					   "storylineSubscriptionsRemoved":null}],
					 "nextBefore":null}
					""".formatted(id), JsonCompareMode.STRICT));
	}

	@Test
	void removedSubscriptionsNoticeCarriesTheTitleReadNowFromTheRemainingEvent() throws Exception {
		long article = EventArticles.insert(this.jdbc, "풀린 흐름에 남았던 기사", null);
		long event = StorylineRows.event(this.jdbc, "OPEN", "model-a", "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of());
		long criteria = this.jdbc.sql("""
				insert into event_assignment_criteria (window_seconds, upper, lower, store, probability, created_at)
				values (259200, 0.8, 0.55, 0.4, 0.5, now()) returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into event_articles (article_id, event_id, published_at, score, decided_by, opened, criteria_id,
				                            assigned_at)
				values (:article, :event, '2026-11-05T00:00:00Z', 0.9, 'FORMULA', true, :criteria, '2026-11-05T00:00:00Z')
				""").param("article", article).param("event", event).param("criteria", criteria).update();
		long id = this.notices.create(this.admin.id(), new StorylineSubscriptionsRemovedContent(
				List.of(new RemovedSubscription(31, event), new RemovedSubscription(32, null))), T1);

		try {
			this.mockMvc.perform(get("/api/notices").with(this.admin))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"notices":[
						  {"id":%d,"kind":"STORYLINE_SUBSCRIPTIONS_REMOVED","revision":1,"createdAt":"2026-11-13T00:00:00Z",
						   "updatedAt":"2026-11-13T00:00:00Z","read":false,"digestFailed":null,"feedStatus":null,"passwordChanged":null,
						   "keyInvalidated":null,"preferencesAdjusted":null,
						   "storylineSubscriptionsRemoved":{"removed":[
						     {"storylineId":31,"title":"풀린 흐름에 남았던 기사"},
						     {"storylineId":32,"title":null}]}}],
						 "nextBefore":null}
						""".formatted(id), JsonCompareMode.STRICT));
		}
		finally {
			StorylineRows.clear(this.jdbc);
			this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", article).update();
			this.jdbc.sql("delete from articles where id = :id").param("id", article).update();
		}
	}

	@Test
	void pagesByFiftyWithACursorToTheNextPage() throws Exception {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			ids.add(readNotice());
		}
		this.mockMvc.perform(get("/api/notices").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notices.length()").value(50))
			.andExpect(jsonPath("$.nextBefore").isEmpty());
		ids.add(readNotice());

		this.mockMvc.perform(get("/api/notices").with(this.admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notices.length()").value(50))
			.andExpect(jsonPath("$.notices[0].id").value(ids.get(50)))
			.andExpect(jsonPath("$.notices[49].id").value(ids.get(1)))
			.andExpect(jsonPath("$.nextBefore").value(ids.get(1)));
		this.mockMvc.perform(get("/api/notices").with(this.admin).param("before", Long.toString(ids.get(1))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notices.length()").value(1))
			.andExpect(jsonPath("$.notices[0].id").value(ids.get(0)))
			.andExpect(jsonPath("$.nextBefore").isEmpty());
	}

	@Test
	void readingMarksOnlyNoticesAtTheRevisionTheScreenSaw() throws Exception {
		long notice = this.notices.create(this.admin.id(), FIRST, T1);
		this.notices.revise(notice, SECOND, T2);

		this.mockMvc.perform(read("""
				{"notices":[{"id":%d,"revision":1}]}""".formatted(notice))).andExpect(status().isNoContent());
		assertThat(this.notices.countUnread(this.admin.id())).as("옛 판의 읽음은 알림을 바꾸지 않는다").isOne();
		this.mockMvc.perform(read("""
				{"notices":[{"id":%d,"revision":2},{"id":999999,"revision":1}]}""".formatted(notice)))
			.andExpect(status().isNoContent());
		assertThat(this.notices.countUnread(this.admin.id())).isZero();
		this.mockMvc.perform(read("""
				{"notices":[]}""")).andExpect(status().isNoContent());
	}

	@Test
	void rejectsABadCursorOrReadRequest() throws Exception {
		this.mockMvc.perform(get("/api/notices").with(this.admin).param("before", "0"))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/api/notices").with(this.admin).param("before", "abc"))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(read(seen(50))).andExpect(status().isNoContent());
		this.mockMvc.perform(read(seen(51))).andExpect(status().isBadRequest());
		this.mockMvc.perform(read("""
				{"notices":[{"id":1,"revision":0}]}""")).andExpect(status().isBadRequest());
		this.mockMvc.perform(read("""
				{"notices":[{"id":1}]}""")).andExpect(status().isBadRequest());
		this.mockMvc.perform(read("""
				{}""")).andExpect(status().isBadRequest());
	}

	@Test
	void readingNeedsTheCsrfHeader() throws Exception {
		long notice = this.notices.create(this.admin.id(), FIRST, T1);

		this.mockMvc
			.perform(post("/api/notices/read").cookie(new Cookie("SESSION", this.admin.session()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"notices":[{"id":%d,"revision":1}]}""".formatted(notice)))
			.andExpect(status().isForbidden());
		assertThat(this.notices.countUnread(this.admin.id())).isOne();
	}

	/** 알림 하나를 만들어 읽음으로 둔다 */
	private long readNotice() {
		long id = this.notices.create(this.admin.id(), FIRST, T1);
		this.notices.markRead(this.admin.id(), List.of(new SeenNotice(id, 1)), T1);
		return id;
	}

	/** 없는 알림 ID 1 ~ n 의 읽음 요청 본문 */
	private static String seen(int n) {
		return IntStream.rangeClosed(1, n)
			.mapToObj(i -> "{\"id\":" + i + ",\"revision\":1}")
			.collect(Collectors.joining(",", "{\"notices\":[", "]}"));
	}

	private MockHttpServletRequestBuilder read(String body) {
		return post("/api/notices/read").with(this.admin).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
