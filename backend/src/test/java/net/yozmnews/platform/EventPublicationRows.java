package net.yozmnews.platform;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** 재처리 시험에서 {@link ResubmissionProbeEvent} 를 발행하고, 이벤트 발행 행을 직접 넣고 · 읽고 · 바꾼다. */
final class EventPublicationRows {

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher publisher;

	private final TransactionTemplate transaction;

	EventPublicationRows(JdbcClient jdbc, ApplicationEventPublisher publisher, TransactionTemplate transaction) {
		this.jdbc = jdbc;
		this.publisher = publisher;
		this.transaction = transaction;
	}

	/** 트랜잭션 안에서 발행한다 — 커밋 뒤에 리스너가 돈다. */
	void publish(String probeId, ResubmissionProbeEvent.Behavior behavior) {
		this.transaction
			.executeWithoutResult((status) -> this.publisher.publishEvent(new ResubmissionProbeEvent(probeId, behavior)));
	}

	/** 이벤트 내용에 {@code probeId} 가 든 발행 행. 완료돼 지워졌으면 비어 있다. */
	Optional<Row> find(String probeId) {
		return this.jdbc.sql("""
				select id, status, completion_attempts, completion_date from event_publication
				where position(:probe in serialized_event) > 0
				""")
			.param("probe", probeId)
			.query((rs, rowNum) -> new Row(rs.getObject("id", UUID.class), rs.getString("status"),
					rs.getInt("completion_attempts"), rs.getObject("completion_date", OffsetDateTime.class)))
			.optional();
	}

	/** 직접 넣는 행의 기본 본문 — 시험 이벤트로 읽을 수 있다 */
	static final String READABLE_BODY = "{\"id\":\"inserted\",\"behavior\":\"SUCCEED\"}";

	/** 시험 이벤트 클래스로 읽을 수 없는 본문 — 없는 열거값 */
	static final String BROKEN_BODY = "{\"id\":\"broken\",\"behavior\":\"NO_SUCH\"}";

	/**
	 * 발행 행을 직접 넣는다 — 본문은 시험 이벤트로 읽을 수 있는 {@link #READABLE_BODY} 다.
	 * @param status 상태 — null 이면 비운다
	 * @param attempts 처리 시도 — null 이면 비운다
	 * @param lastResubmittedAt 마지막 재제출 시각 — null 이면 비운다
	 */
	UUID insert(String eventType, String listenerId, Instant publishedAt, @Nullable String status,
			@Nullable Integer attempts, @Nullable Instant lastResubmittedAt) {
		return insert(UUID.randomUUID(), eventType, listenerId, publishedAt, status, attempts, lastResubmittedAt);
	}

	/** 정해 둔 ID 로 발행 행을 직접 넣는다 — ID 순서를 보는 시험에 쓴다 */
	UUID insert(UUID id, String eventType, String listenerId, Instant publishedAt, @Nullable String status,
			@Nullable Integer attempts, @Nullable Instant lastResubmittedAt) {
		return insert(id, eventType, listenerId, publishedAt, status, attempts, lastResubmittedAt, READABLE_BODY);
	}

	/** 본문을 정해 발행 행을 직접 넣는다 — 읽을 수 없는 본문을 보는 시험에 쓴다 */
	UUID insert(UUID id, String eventType, String listenerId, Instant publishedAt, @Nullable String status,
			@Nullable Integer attempts, @Nullable Instant lastResubmittedAt, String body) {
		this.jdbc.sql("""
				insert into event_publication
				  (id, listener_id, event_type, serialized_event, publication_date, status, completion_attempts,
				   last_resubmission_date)
				values (:id, :listenerId, :eventType, :body, :publishedAt, :status, :attempts, :lastResubmittedAt)
				""")
			.param("id", id)
			.param("body", body)
			.param("listenerId", listenerId)
			.param("eventType", eventType)
			.param("publishedAt", publishedAt.atOffset(ZoneOffset.UTC))
			.param("status", status)
			.param("attempts", attempts)
			.param("lastResubmittedAt", (lastResubmittedAt != null) ? lastResubmittedAt.atOffset(ZoneOffset.UTC) : null)
			.update();
		return id;
	}

	/** 처리 시도를 바꾼다 */
	void setAttempts(UUID id, int attempts) {
		this.jdbc.sql("update event_publication set completion_attempts = :attempts where id = :id")
			.param("attempts", attempts)
			.param("id", id)
			.update();
	}

	/** 행이 남아 있는지 */
	boolean exists(UUID id) {
		return this.jdbc.sql("select count(*) from event_publication where id = :id")
			.param("id", id)
			.query(Integer.class)
			.single() == 1;
	}

	void deleteAll() {
		this.jdbc.sql("delete from event_publication").update();
	}

	record Row(UUID id, String status, int attempts, @Nullable OffsetDateTime completionDate) {
	}

}
