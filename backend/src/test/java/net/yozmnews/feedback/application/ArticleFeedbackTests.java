package net.yozmnews.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.ArticleFeedback;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 소비자 조회 — 싫어요 기사와 기간의 이력.
 * <p>
 * 지키는 규칙: feedback-R8 · feedback-R4(이력의 순서)
 * <p>
 * 현재 상태 · 이력 행은 SQL 로 넣는다 — 이력의 시각 순과 행 순이 어긋난 입력은 기록 흐름이 시험에서 만들기 어렵다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleFeedbackTests {

	private static final UserId USER = new UserId(42);

	private static final UserId OTHER = new UserId(43);

	@Autowired
	ArticleFeedback feedback;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from feedback").update();
		this.jdbc.sql("delete from feedback_events").update();
	}

	@Test
	void dislikedAmongAnswersOnlyTheUsersDislikesAmongTheAskedArticles() {
		state(USER, 901, "DISLIKE");
		state(USER, 902, "LIKE");
		state(USER, 903, "DISLIKE");
		state(OTHER, 904, "DISLIKE");

		assertThat(this.feedback.dislikedAmong(USER, ids(901, 902, 904, 905, 901)))
			.containsExactly(new ArticleId(901));
		assertThat(this.feedback.dislikedAmong(OTHER, ids(901, 904))).containsExactly(new ArticleId(904));
		assertThat(this.feedback.dislikedAmong(USER, List.of())).isEmpty();
	}

	@Test
	void theLimitCountsDistinctArticles() {
		List<ArticleId> limit = new ArrayList<>(
				LongStream.rangeClosed(1, ArticleFeedback.MAX_ARTICLES).mapToObj(ArticleId::new).toList());
		state(USER, 1, "DISLIKE");

		// 같은 기사가 두 번 와도 한 번으로 센다 — 1000개 + 반복 하나는 상한 안
		limit.add(new ArticleId(1));
		assertThat(this.feedback.dislikedAmong(USER, limit)).containsExactly(new ArticleId(1));
		List<ArticleId> over = new ArrayList<>(limit);
		over.add(new ArticleId(ArticleFeedback.MAX_ARTICLES + 1));
		assertThatIllegalArgumentException().isThrownBy(() -> this.feedback.dislikedAmong(USER, over));
	}

	@Test
	void changesAreTheRowsOfTheHalfOpenPeriodInRecordedOrder() {
		event(USER, 901, "LIKE", 11, "2026-11-01T00:00:00Z");
		event(USER, 902, "LIKE", 11, "2026-11-02T00:00:00Z");
		// 뒤에 기록했지만 시각은 앞선다 — 순서는 기록한 순서(행 순)다
		event(USER, 902, "DISLIKE", 12, "2026-11-01T12:00:00Z");
		event(USER, 903, "NONE", 12, "2026-11-03T00:00:00Z");
		event(OTHER, 901, "LIKE", 13, "2026-11-01T06:00:00Z");

		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-11-03T00:00:00Z")))
			.containsExactly(change(901, FeedbackValue.LIKE, 11, "2026-11-01T00:00:00Z"),
					change(902, FeedbackValue.LIKE, 11, "2026-11-02T00:00:00Z"),
					change(902, FeedbackValue.DISLIKE, 12, "2026-11-01T12:00:00Z"));
		// 끝은 제외 · 시작은 포함
		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-03T00:00:00Z"),
				Instant.parse("2026-11-04T00:00:00Z")))
			.containsExactly(change(903, FeedbackValue.NONE, 12, "2026-11-03T00:00:00Z"));
		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-03T00:00:00Z"),
				Instant.parse("2026-11-03T00:00:00Z")))
			.isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> this.feedback.changes(USER,
				Instant.parse("2026-11-03T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z")));
	}

	private void state(UserId user, long article, String value) {
		this.jdbc.sql("""
				insert into feedback (user_id, article_id, value, digest_id, updated_at)
				values (:user, :article, :value, 1, now())
				""").param("user", user.value()).param("article", article).param("value", value).update();
	}

	private void event(UserId user, long article, String value, long digest, String at) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, article_id, value, digest_id, occurred_at)
				values (:user, :article, :value, :digest, :at)
				""")
			.param("user", user.value())
			.param("article", article)
			.param("value", value)
			.param("digest", digest)
			.param("at", Instant.parse(at).atOffset(ZoneOffset.UTC))
			.update();
	}

	private static FeedbackChange change(long article, FeedbackValue value, long digest, String at) {
		return new FeedbackChange(new ArticleId(article), value, digest, Instant.parse(at));
	}

	private static List<ArticleId> ids(long... values) {
		return LongStream.of(values).mapToObj(ArticleId::new).toList();
	}

}
