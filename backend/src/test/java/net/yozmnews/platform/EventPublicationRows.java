package net.yozmnews.platform;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** 재처리 시험에서 {@link ResubmissionProbeEvent} 를 발행하고 그 발행 행을 읽는다. */
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

	void deleteAll() {
		this.jdbc.sql("delete from event_publication").update();
	}

	record Row(UUID id, String status, int attempts, @Nullable OffsetDateTime completionDate) {
	}

}
