package net.yozmnews.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackRows;
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
 * 소비자 조회 — 싫어요 사건(합쳐진 사건을 풀어 마지막으로 누른 값)과 기간의 이력(기록한 사건 · 지금 사건).
 * <p>
 * 지키는 규칙: feedback-R8 · feedback-R4(이력의 순서)
 * <p>
 * 실제 PostgreSQL 과 실제 사건 풀기({@code event})로 돈다. 현재 상태 · 이력 행과 사건 행은 SQL 로 넣는다 — 합쳐진 사건들에 서로 다른 값이
 * 남은 입력과 시각 순 · 행 순이 어긋난 입력은 기록 흐름이 시험에서 만들기 어렵다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EventFeedbackTests {

	private static final UserId USER = new UserId(42);

	private static final UserId OTHER = new UserId(43);

	@Autowired
	EventFeedback feedback;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		FeedbackRows.clear(this.jdbc);
	}

	@Test
	void anUnmergedEventAnswersWithItsOwnLastValue() {
		pressed(USER, 901, "DISLIKE");
		pressed(USER, 902, "LIKE");
		pressed(USER, 903, "DISLIKE");
		pressed(USER, 903, "NONE");
		pressed(OTHER, 904, "DISLIKE");

		assertThat(this.feedback.dislikedAmong(USER, ids(901, 902, 903, 904, 905, 901)))
			.containsExactly(new EventId(901));
		assertThat(this.feedback.dislikedAmong(OTHER, ids(901, 904))).containsExactly(new EventId(904));
		assertThat(this.feedback.dislikedAmong(USER, List.of())).isEmpty();
	}

	@Test
	void aLaterLikeOnTheSurvivorOutweighsAnEarlierDislikeOnAMergedEvent() {
		long merged = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, merged, survivor);
		pressed(USER, merged, "DISLIKE");
		pressed(USER, survivor, "LIKE");

		assertThat(this.feedback.dislikedAmong(USER, ids(survivor))).isEmpty();
	}

	@Test
	void aLaterDislikeOnAMergedEventCountsForTheSurvivor() {
		// 순서를 뒤집으면 — 합쳐진 사건의 싫어요가 마지막이라 남는 사건을 뺀다
		long merged = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, merged, survivor);
		pressed(USER, survivor, "LIKE");
		pressed(USER, merged, "DISLIKE");

		assertThat(this.feedback.dislikedAmong(USER, ids(survivor))).containsExactly(new EventId(survivor));
	}

	@Test
	void aLastRevertOnTheSurvivorLeavesNoDislikeEvenIfAMergedEventKeepsOne() {
		long merged = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, merged, survivor);
		pressed(USER, merged, "DISLIKE");
		pressed(USER, survivor, "LIKE");
		pressed(USER, survivor, "NONE");

		// 합쳐진 사건의 현재 상태는 아직 싫어요지만 마지막으로 누른 값은 없음이다
		assertThat(FeedbackRows.current(this.jdbc, USER.value())).containsExactly(merged + ":DISLIKE:1");
		assertThat(this.feedback.dislikedAmong(USER, ids(survivor))).isEmpty();
	}

	@Test
	void aRevertAfterADislikeOnTheSurvivorLeavesNoDislike() {
		// 되돌리기 앞의 값이 싫어요여도 마지막으로 누른 값은 없음이다 — 없음 행을 건너뛰고 고르면 싫어요가 남는다
		long merged = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, merged, survivor);
		pressed(USER, merged, "DISLIKE");
		pressed(USER, survivor, "DISLIKE");
		pressed(USER, survivor, "NONE");

		assertThat(this.feedback.dislikedAmong(USER, ids(survivor))).isEmpty();
	}

	@Test
	void theLimitCountsDistinctEvents() {
		List<EventId> limit = new ArrayList<>(
				LongStream.rangeClosed(1, EventFeedback.MAX_EVENTS).mapToObj(EventId::new).toList());
		pressed(USER, 1, "DISLIKE");

		// 같은 사건이 두 번 와도 한 번으로 센다 — 1000개 + 반복 하나는 상한 안
		assertThat(EventFeedback.MAX_EVENTS).isEqualTo(1000);
		limit.add(new EventId(1));
		assertThat(this.feedback.dislikedAmong(USER, limit)).containsExactly(new EventId(1));
		List<EventId> over = new ArrayList<>(limit);
		over.add(new EventId(EventFeedback.MAX_EVENTS + 1));
		assertThatIllegalArgumentException().isThrownBy(() -> this.feedback.dislikedAmong(USER, over));
	}

	@Test
	void eachChangeCarriesTheRecordedEventAndTheCurrentOne() {
		long merged = FeedbackRows.event(this.jdbc);
		long survivor = FeedbackRows.event(this.jdbc);
		long removed = FeedbackRows.event(this.jdbc);
		FeedbackRows.merge(this.jdbc, merged, survivor);
		FeedbackRows.deleteEvent(this.jdbc, removed);
		history(USER, merged, "LIKE", 11, "2026-11-01T00:00:00Z");
		history(USER, removed, "DISLIKE", 11, "2026-11-01T01:00:00Z");

		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-11-02T00:00:00Z")))
			.containsExactly(
					new FeedbackChange(new EventId(merged), new EventId(survivor), FeedbackValue.LIKE, 11,
							Instant.parse("2026-11-01T00:00:00Z")),
					new FeedbackChange(new EventId(removed), null, FeedbackValue.DISLIKE, 11,
							Instant.parse("2026-11-01T01:00:00Z")));
	}

	@Test
	void anEventThatStaysCarriesItselfAsTheCurrentOne() {
		// 대조 — 그대로인 사건은 기록한 사건과 지금 사건이 같다
		long event = FeedbackRows.event(this.jdbc);
		history(USER, event, "LIKE", 11, "2026-11-01T00:00:00Z");

		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-11-02T00:00:00Z")))
			.containsExactly(new FeedbackChange(new EventId(event), new EventId(event), FeedbackValue.LIKE, 11,
					Instant.parse("2026-11-01T00:00:00Z")));
	}

	@Test
	void changesAreTheRowsOfTheHalfOpenPeriodInRecordedOrder() {
		history(USER, 901, "LIKE", 11, "2026-11-01T00:00:00Z");
		history(USER, 902, "LIKE", 11, "2026-11-02T00:00:00Z");
		// 뒤에 기록했지만 시각은 앞선다 — 순서는 기록한 순서(행 순)다
		history(USER, 902, "DISLIKE", 12, "2026-11-01T12:00:00Z");
		history(USER, 903, "NONE", 12, "2026-11-03T00:00:00Z");
		history(OTHER, 901, "LIKE", 13, "2026-11-01T06:00:00Z");

		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-11-03T00:00:00Z")))
			.extracting(change -> change.recorded().value() + ":" + change.value() + ":" + change.digestId())
			.containsExactly("901:LIKE:11", "902:LIKE:11", "902:DISLIKE:12");
		// 끝은 제외 · 시작은 포함
		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-03T00:00:00Z"),
				Instant.parse("2026-11-04T00:00:00Z")))
			.extracting(change -> change.recorded().value() + ":" + change.value())
			.containsExactly("903:NONE");
		assertThat(this.feedback.changes(USER, Instant.parse("2026-11-03T00:00:00Z"),
				Instant.parse("2026-11-03T00:00:00Z")))
			.isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> this.feedback.changes(USER,
				Instant.parse("2026-11-03T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z")));
	}

	/** 누르기 한 번의 결과 — 이력 한 줄을 더하고 현재 상태를 그 값으로(없음이면 지움) 둔다 */
	private void pressed(UserId user, long event, String value) {
		history(user, event, value, 1, "2026-11-01T00:00:00Z");
		this.jdbc.sql("delete from feedback where user_id = :user and event_id = :event")
			.param("user", user.value())
			.param("event", event)
			.update();
		if (!value.equals("NONE")) {
			this.jdbc.sql("""
					insert into feedback (user_id, event_id, value, digest_id, updated_at)
					values (:user, :event, :value, 1, now())
					""").param("user", user.value()).param("event", event).param("value", value).update();
		}
	}

	private void history(UserId user, long event, String value, long digest, String at) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, event_id, value, digest_id, occurred_at)
				values (:user, :event, :value, :digest, :at)
				""")
			.param("user", user.value())
			.param("event", event)
			.param("value", value)
			.param("digest", digest)
			.param("at", Instant.parse(at).atOffset(ZoneOffset.UTC))
			.update();
	}

	private static List<EventId> ids(long... values) {
		return LongStream.of(values).mapToObj(EventId::new).toList();
	}

}
