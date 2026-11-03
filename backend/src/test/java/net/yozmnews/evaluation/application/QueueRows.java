package net.yozmnews.evaluation.application;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;

/** 시험 픽스처 — 평가 대기 행 읽기 */
final class QueueRows {

	private QueueRows() {
	}

	/** 대기 행의 평가 가능 시각 */
	static Instant eligibleAt(JdbcClient jdbc, long article) {
		return jdbc.sql("select eligible_at from evaluation_queue where article_id = :article")
			.param("article", article)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

}
