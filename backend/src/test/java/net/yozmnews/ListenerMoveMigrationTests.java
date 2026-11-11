package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import net.yozmnews.article.ArticleStored;
import net.yozmnews.article.ArticlesRemoved;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;

/**
 * 리스너 · 반복 작업을 tag 로 옮기는 마이그레이션(V27) — 새 리스너 ID 문자열이 실제 리스너가 이벤트 발행 레지스트리에 쓰는 ID 와 같은지,
 * 그리고 실제 PostgreSQL 의 별도 스키마에 V26 까지만 올려 옛 리스너 ID 의 발행 · 옛 클래스의 예약 작업을 넣고 끝까지 올린 뒤, 미완료 발행이
 * 새 리스너로 옮겨지고 나뉜 몫이 복제되는지, 예약 · 대기 작업만 지워지는지 본다. 앱이 쓰는 스키마와 섞이지 않게 스키마를 따로 두고 테스트 뒤
 * 지운다.
 * <p>
 * 옛 리스너 ID 문자열은 옛 클래스가 없어 여기서 대조하지 못한다 — 마이그레이션 원문의 리터럴과 같은 문자열을 넣어 읽는다.
 */
@JdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ListenerMoveMigrationTests {

	private static final String SCHEMA = "v27_listener_moves";

	private static final String OLD_STORED = "net.yozmnews.evaluation.application.ArticleEventsListener.on(net.yozmnews.article.ArticleStored)";

	private static final String OLD_REMOVED = "net.yozmnews.evaluation.application.ArticleEventsListener.on(net.yozmnews.article.ArticlesRemoved)";

	private static final String OTHER = "net.yozmnews.other.application.OtherListener.on(net.yozmnews.article.ArticleStored)";

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void dropSchema() {
		this.jdbc.sql("drop schema if exists " + SCHEMA + " cascade").update();
	}

	@Test
	void theMigrationNamesTheListenersByTheIdsTheyRegisterWith() throws Exception {
		assertThat(List.of(listenerId("net.yozmnews.tag.application.ArticleEventsListener", ArticleStored.class),
				listenerId("net.yozmnews.tag.application.ArticleEventsListener", ArticlesRemoved.class),
				listenerId("net.yozmnews.evaluation.application.JudgementRemovalListener", ArticlesRemoved.class)))
			.containsExactly("net.yozmnews.tag.application.ArticleEventsListener.on(net.yozmnews.article.ArticleStored)",
					"net.yozmnews.tag.application.ArticleEventsListener.on(net.yozmnews.article.ArticlesRemoved)",
					"net.yozmnews.evaluation.application.JudgementRemovalListener.on(net.yozmnews.article.ArticlesRemoved)");
	}

	@Test
	void pendingPublicationsFollowTheMovedListenersAndPendingJobsOfTheMovedClassesAreDropped() throws Exception {
		flyway("26").migrate();
		publication("00000000-0000-0000-0000-000000000001", OLD_STORED, "net.yozmnews.article.ArticleStored",
				"{\"articleId\":7}", null, "FAILED", 2);
		publication("00000000-0000-0000-0000-000000000002", OLD_REMOVED, "net.yozmnews.article.ArticlesRemoved",
				"{\"articleIds\":[7,8]}", null, "PUBLISHED", 1);
		publication("00000000-0000-0000-0000-000000000003", OLD_REMOVED, "net.yozmnews.article.ArticlesRemoved",
				"{\"articleIds\":[9]}", "2026-11-02T00:05:00Z", "COMPLETED", 1);
		publication("00000000-0000-0000-0000-000000000004", OTHER, "net.yozmnews.article.ArticleStored",
				"{\"articleId\":7}", null, "PUBLISHED", 0);
		job("00000000-0000-0000-0000-00000000000a", "evaluate-pending-articles", "SCHEDULED");
		job("00000000-0000-0000-0000-00000000000b", "backfill-missing-tags", "ENQUEUED");
		job("00000000-0000-0000-0000-00000000000c", "evaluate-pending-articles", "AWAITING");
		job("00000000-0000-0000-0000-00000000000d", "evaluate-pending-articles", "PROCESSING");
		job("00000000-0000-0000-0000-00000000000e", "backfill-missing-tags", "SUCCEEDED");
		job("00000000-0000-0000-0000-00000000000f", "poll-due-feeds", "SCHEDULED");

		flyway(null).migrate();

		String tagStored = listenerId("net.yozmnews.tag.application.ArticleEventsListener", ArticleStored.class);
		String tagRemoved = listenerId("net.yozmnews.tag.application.ArticleEventsListener", ArticlesRemoved.class);
		String judgementRemoved = listenerId("net.yozmnews.evaluation.application.JudgementRemovalListener",
				ArticlesRemoved.class);
		assertThat(this.jdbc.sql("select id::text as id, listener_id from " + SCHEMA
				+ ".event_publication where id::text like '00000000-%' order by id")
			.query()
			.listOfRows()).containsExactly(
					Map.of("id", "00000000-0000-0000-0000-000000000001", "listener_id", tagStored),
					Map.of("id", "00000000-0000-0000-0000-000000000002", "listener_id", tagRemoved),
					Map.of("id", "00000000-0000-0000-0000-000000000003", "listener_id", OLD_REMOVED),
					Map.of("id", "00000000-0000-0000-0000-000000000004", "listener_id", OTHER));
		// 나뉜 몫은 새 리스너마다 한 행 — 복제 행은 새 ID 이고 이벤트 · 상태 · 시도 횟수는 원래 행 그대로다
		assertThat(this.jdbc.sql("select listener_id, event_type, serialized_event, "
				+ "to_char(publication_date at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') as published, "
				+ "completion_date is null as pending, status, completion_attempts from " + SCHEMA
				+ ".event_publication where id::text not like '00000000-%'")
			.query()
			.listOfRows()).containsExactly(Map.of("listener_id", judgementRemoved, "event_type",
					"net.yozmnews.article.ArticlesRemoved", "serialized_event", "{\"articleIds\":[7,8]}", "published",
					"2026-11-02 00:00:00", "pending", true, "status", "PUBLISHED", "completion_attempts", 1));
		assertThat(this.jdbc.sql("select recurringJobId || ':' || state from " + SCHEMA + ".jobrunr_jobs order by id")
			.query(String.class)
			.list()).containsExactly("evaluate-pending-articles:PROCESSING", "backfill-missing-tags:SUCCEEDED",
					"poll-due-feeds:SCHEDULED");
	}

	private static String listenerId(String className, Class<?> eventType) throws Exception {
		Class<?> listener = Class.forName(className);
		return new TransactionalApplicationListenerMethodAdapter("listener", listener, listener.getMethod("on", eventType))
			.getListenerId();
	}

	private void publication(String id, String listener, String eventType, String event, @Nullable String completedAt,
			String status, int attempts) {
		this.jdbc.sql("insert into " + SCHEMA + ".event_publication (id, listener_id, event_type, serialized_event, "
				+ "publication_date, completion_date, status, completion_attempts) values (cast(:id as uuid), :listener, "
				+ ":type, :event, timestamptz '2026-11-02T00:00:00Z', cast(:completed as timestamptz), :status, "
				+ ":attempts)")
			.param("id", id)
			.param("listener", listener)
			.param("type", eventType)
			.param("event", event)
			.param("completed", completedAt)
			.param("status", status)
			.param("attempts", attempts)
			.update();
	}

	private void job(String id, String recurringJob, String state) {
		this.jdbc.sql("insert into " + SCHEMA + ".jobrunr_jobs (id, version, jobAsJson, jobSignature, state, createdAt, "
				+ "updatedAt, scheduledAt, recurringJobId) values (:id, 1, '{}', 'signature', :state, now(), now(), "
				+ "now(), :recurring)")
			.param("id", id)
			.param("state", state)
			.param("recurring", recurringJob)
			.update();
	}

	private Flyway flyway(@Nullable String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		return ((target != null) ? configuration.target(target) : configuration).load();
	}

}
