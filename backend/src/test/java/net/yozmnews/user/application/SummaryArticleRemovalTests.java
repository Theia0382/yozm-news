package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.UserStorylineFixtures;
import net.yozmnews.user.domain.summary.StorylineSummary;
import net.yozmnews.user.domain.summary.StorylineSummaryRepository;
import net.yozmnews.user.domain.summary.SummaryAttempt;
import net.yozmnews.user.domain.summary.SummaryText;
import net.yozmnews.user.domain.userstoryline.UserStorylineRepository;
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
 * 지운 기사 따르기 — 지운 기사를 넣은 정리는 사용자를 가로질러 지워지고 시도 기록은 남는다. 그 기사를 넣지 않은 정리는 그대로다. 기사가 한 문장의
 * 상한보다 많아도 모두 본다. 다시 처리해도 결과가 같다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class SummaryArticleRemovalTests {

	private static final Instant AT = Instant.parse("2026-11-20T00:00:00Z");

	@Autowired
	SummaryArticleRemoval removal;

	@Autowired
	StorylineSummaryRepository summaries;

	@Autowired
	UserStorylineRepository userStorylines;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	UserId alice;

	UserId bob;

	@BeforeEach
	@AfterEach
	void clean() {
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void summariesHoldingARemovedArticleGoAndTheirAttemptsStay() {
		this.alice = UserStorylineFixtures.user(this.jdbc, "alice@example.com");
		this.bob = UserStorylineFixtures.user(this.jdbc, "bob@example.com");
		long holding = summarized(this.alice, Map.of(101L, 1_001L, 102L, 1_002L));
		long alsoHolding = summarized(this.bob, Map.of(103L, 1_002L));
		long clean = summarized(this.alice, Map.of(104L, 1_004L));

		assertThat(this.removal.apply(List.of(new ArticleId(1_002), new ArticleId(9_999)))).isEqualTo(2);

		assertThat(this.summaries.load(this.alice)).extracting(StorylineSummary::userStorylineId).containsExactly(clean);
		assertThat(this.summaries.load(this.bob)).isEmpty();
		assertThat(this.summaries.attempts(this.alice)).extracting(SummaryAttempt::userStorylineId)
			.containsExactly(holding, clean);
		assertThat(this.summaries.attempts(this.bob)).extracting(SummaryAttempt::userStorylineId)
			.containsExactly(alsoHolding);
		// 다시 처리해도 같다
		assertThat(this.removal.apply(List.of(new ArticleId(1_002)))).isZero();
	}

	@Test
	void articlesBeyondOneBatchAreAllLookedAt() {
		this.alice = UserStorylineFixtures.user(this.jdbc, "alice@example.com");
		summarized(this.alice, Map.of(101L, 5_000L));
		List<ArticleId> removed = new ArrayList<>();
		for (long id = 3_000; id <= 5_000; id++) {
			removed.add(new ArticleId(id));
		}

		assertThat(this.removal.apply(removed)).isEqualTo(1);

		assertThat(this.summaries.load(this.alice)).isEmpty();
	}

	@Test
	void noRemovedArticlesChangeNothing() {
		this.alice = UserStorylineFixtures.user(this.jdbc, "alice@example.com");
		summarized(this.alice, Map.of(101L, 1_001L));

		assertThat(this.removal.apply(List.of())).isZero();

		assertThat(this.summaries.load(this.alice)).hasSize(1);
	}

	/** 그 기사들을 넣은 정리와 시도 기록 하나를 가진 사용자 스토리라인 */
	private long summarized(UserId user, Map<Long, Long> events) {
		return this.transaction.execute(status -> {
			long id = this.userStorylines.create(user, AT);
			SummaryText text = new SummaryText("제목", "요약", "최근");
			this.summaries.create(new StorylineSummary(id, text, Map.of(11L, text), events, "ANTHROPIC", "model", 10, 2,
					AT, AT, 0));
			this.summaries.recordStored(id, 10, 2);
			return id;
		});
	}

}
