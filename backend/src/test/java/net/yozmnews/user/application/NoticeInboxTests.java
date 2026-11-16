package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventArticles;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.user.domain.notice.NoticeRepository;
import net.yozmnews.user.domain.notice.RemovedSubscription;
import net.yozmnews.user.domain.notice.StorylineSubscriptionsRemovedContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림함의 사라진 구독 제목 — 알림은 사건 ID 만 담고, 쪽을 읽을 때 그 사건의 지금 대표 기사 제목을 읽는다. 기사가 지워지면 제목도 없고, 사건이
 * 합쳐졌으면 남는 사건의 대표 기사다. 실제 PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: user-R54
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class NoticeInboxTests {

	private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

	@Autowired
	NoticeInbox inbox;

	@Autowired
	NoticeRepository notices;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final List<Long> articles = new ArrayList<>();

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from users").update();
		StorylineRows.clear(this.jdbc);
		for (long article : this.articles) {
			this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", article).update();
			this.jdbc.sql("delete from articles where id = :id").param("id", article).update();
		}
		this.articles.clear();
	}

	@Test
	void theTitleIsTheRemainingEventsRepresentativeArticleReadNow() {
		UserId user = user();
		long remaining = eventWithArticle("풀린 흐름에 남았던 기사");
		notice(user, new RemovedSubscription(31, remaining), new RemovedSubscription(32, null));

		Map<Long, String> titles = this.inbox.page(user, null).eventTitles();

		assertThat(titles).containsExactly(Map.entry(remaining, "풀린 흐름에 남았던 기사"));
	}

	@Test
	void aDeletedArticleLeavesNoTitle() {
		UserId user = user();
		long remaining = eventWithArticle("지울 기사");
		notice(user, new RemovedSubscription(31, remaining));
		long article = this.articles.getLast();
		this.jdbc.sql("delete from event_articles where article_id = :id").param("id", article).update();
		this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", article).update();
		this.jdbc.sql("delete from articles where id = :id").param("id", article).update();

		// 지운 기사의 제목은 알림에 남지 않는다 — 알림은 사건 ID 만 담았다
		assertThat(this.inbox.page(user, null).eventTitles()).isEmpty();
	}

	@Test
	void aMergedEventShowsItsSurvivorsRepresentativeArticle() {
		UserId user = user();
		long survivor = eventWithArticle("남는 사건의 기사");
		long merged = StorylineRows.event(this.jdbc, "OPEN", "model-a", "[1,0]", 1, "2026-11-05T00:00:00Z",
				"2026-11-05T00:00:00Z", Map.of());
		StorylineRows.merge(this.jdbc, merged, survivor);
		notice(user, new RemovedSubscription(31, merged));

		assertThat(this.inbox.page(user, null).eventTitles()).containsExactly(Map.entry(merged, "남는 사건의 기사"));
	}

	private UserId user() {
		return new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('alice@example.com', 'alice', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single());
	}

	private void notice(UserId user, RemovedSubscription... removed) {
		this.transaction.executeWithoutResult(
				status -> this.notices.create(user, new StorylineSubscriptionsRemovedContent(List.of(removed)), NOW));
	}

	/** 기사 하나가 배정된 사건 — 그 기사가 대표 기사다 */
	private long eventWithArticle(String title) {
		long article = EventArticles.insert(this.jdbc, title, null);
		this.articles.add(article);
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
		return event;
	}

}
