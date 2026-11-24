package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.TopicInterestNow;
import net.yozmnews.user.UserTables;
import net.yozmnews.user.application.TopicSuggestionDeclining.DeclineOutcome;
import net.yozmnews.user.application.TopicSuggestionDeclining.UnknownTopicException;
import net.yozmnews.user.domain.suggestion.RecordedDecline;
import net.yozmnews.user.domain.suggestion.TopicSuggestionDeclineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 구독 제안 거절 — 화면이 본 버전 확인, 중분류 키 확인, 거절 때 관심을 공급에서 받아 적음, 같은 버전 · 키를 다시 거절하면 덮어씀. 공급
 * ({@link TopicInterestNow})은 시험이 정한 값을 내는 대체물이고, 저장은 실제 PostgreSQL 이다.
 * <p>
 * 지키는 규칙: user-R64
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TopicSuggestionDecliningTests {

	private static final Instant AT = Instant.parse("2026-11-18T03:00:00Z");

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	TopicSuggestionDeclineRepository declines;

	@Autowired
	JdbcClient jdbc;

	private UserId user;

	@BeforeEach
	void prepare() {
		clean();
		this.user = new UserId(this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('decline@example.com', '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single());
	}

	@AfterEach
	void clean() {
		UserTables.deleteAll(this.jdbc);
	}

	@Test
	void aDeclineIsWrittenWithTheLevelTheSupplyGives() {
		DeclineOutcome outcome = declining(0.4).decline(this.user, active(), "economy.markets", AT);

		assertThat(outcome).isEqualTo(new DeclineOutcome.Declined("economy.markets"));
		assertThat(this.declines.load(this.user))
			.containsExactly(new RecordedDecline(active(), "economy.markets", 0.4, AT));
	}

	@Test
	void decliningTheSameKeyAgainReplacesTheLevelAndTime() {
		declining(0.4).decline(this.user, active(), "economy.markets", AT);
		Instant later = AT.plusSeconds(3600);

		declining(0.55).decline(this.user, active(), "economy.markets", later);

		assertThat(this.declines.load(this.user))
			.containsExactly(new RecordedDecline(active(), "economy.markets", 0.55, later));
	}

	@Test
	void anotherVersionThanTheActiveOneIsNotWritten() {
		AtomicInteger asked = new AtomicInteger();
		TopicInterestNow counting = (user, tagSet, subtopic) -> {
			asked.incrementAndGet();
			return OptionalDouble.of(0.4);
		};
		TagSetId other = new TagSetId(active().value() + 1_000);

		DeclineOutcome outcome = new TopicSuggestionDeclining(this.tagSets, counting, this.declines).decline(this.user,
				other, "economy.markets", AT);

		assertThat(outcome).isEqualTo(new DeclineOutcome.TagSetChanged(active()));
		assertThat(asked).hasValue(0);
		assertThat(this.declines.load(this.user)).isEmpty();
	}

	@Test
	void aSupplyThatCannotAnswerMeansTheTagSetChanged() {
		TopicInterestNow silent = (user, tagSet, subtopic) -> OptionalDouble.empty();

		DeclineOutcome outcome = new TopicSuggestionDeclining(this.tagSets, silent, this.declines).decline(this.user,
				active(), "economy.markets", AT);

		assertThat(outcome).isEqualTo(new DeclineOutcome.TagSetChanged(active()));
		assertThat(this.declines.load(this.user)).isEmpty();
	}

	@Test
	void onlyASubtopicKeyOfThatVersionCanBeDeclined() {
		for (String key : new String[] { "economy", "sports.nowhere", "us" }) {
			assertThatThrownBy(() -> declining(0.4).decline(this.user, active(), key, AT))
				.isInstanceOf(UnknownTopicException.class);
		}
		assertThat(this.declines.load(this.user)).isEmpty();
	}

	private TopicSuggestionDeclining declining(double level) {
		return new TopicSuggestionDeclining(this.tagSets, (user, tagSet, subtopic) -> OptionalDouble.of(level),
				this.declines);
	}

	private TagSetId active() {
		return this.tagSets.activeTagSet().id();
	}

}
