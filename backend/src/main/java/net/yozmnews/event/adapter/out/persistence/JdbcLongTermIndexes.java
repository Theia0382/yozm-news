package net.yozmnews.event.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.event.domain.event.LongTermIndexes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link LongTermIndexes} 의 JdbcClient 구현 — 인덱스 이름 · 식 · 조건의 정본이다 · docs/specs/event.md §4.
 * <p>
 * 이름은 {@code events_closed_centroid_} + 모델 버전 SHA-256 앞 16 hex, 식은 {@code centroid::halfvec(차원)} 의
 * {@code halfvec_cosine_ops} HNSW, 조건은 {@code status = 'CLOSED' AND model_version = '모델 버전'} 이다. 모델 버전은 SQL 문자열
 * 리터럴로 이스케이프한다. {@code CREATE INDEX CONCURRENTLY} 라 자동 커밋 연결에서 한 문장씩 돌고, 실패로 남은 쓸 수 없는(invalid) 같은
 * 이름의 인덱스는 지우고 다시 만든다.
 */
@Repository
class JdbcLongTermIndexes implements LongTermIndexes {

	static final String PREFIX = "events_closed_centroid_";

	private static final Logger logger = LoggerFactory.getLogger(JdbcLongTermIndexes.class);

	private final JdbcClient jdbc;

	JdbcLongTermIndexes(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int ensure() {
		List<Map<String, Object>> versions = this.jdbc.sql("""
				select model_version, min(vector_dims(centroid)) as low, max(vector_dims(centroid)) as high
				from events where status = 'CLOSED' group by model_version order by model_version
				""").query().listOfRows();
		int created = 0;
		for (Map<String, Object> version : versions) {
			String model = (String) version.get("model_version");
			int low = ((Number) version.get("low")).intValue();
			int high = ((Number) version.get("high")).intValue();
			if (low != high) {
				logger.warn("닫힌 사건의 중심 차원이 모델 버전 안에서 달라 장기 검색 인덱스를 만들지 않았다 — {}: {} ~ {}", model, low, high);
				continue;
			}
			try {
				if (create(model, low)) {
					created++;
				}
			}
			catch (DataAccessException ex) {
				logger.warn("장기 검색 인덱스를 만들지 못했다 — {}: {}", model, ex.getMostSpecificCause().getMessage());
			}
		}
		return created;
	}

	/** 그 모델 버전의 인덱스가 쓸 수 있는 상태로 있으면 그대로, 없거나 쓸 수 없으면 만든다 — 만들었으면 true */
	private boolean create(String model, int dimensions) {
		String name = name(model);
		Optional<Boolean> valid = this.jdbc.sql("""
				select i.indisvalid from pg_index i join pg_class c on c.oid = i.indexrelid
				where c.relname = :name and c.relnamespace = to_regnamespace(current_schema())
				""").param("name", name).query(Boolean.class).optional();
		if (valid.isPresent() && valid.get()) {
			return false;
		}
		if (valid.isPresent()) {
			this.jdbc.sql("drop index concurrently if exists " + name).update();
		}
		this.jdbc.sql("create index concurrently if not exists " + name
				+ " on events using hnsw ((centroid::halfvec(" + dimensions + ")) halfvec_cosine_ops)"
				+ " where status = 'CLOSED' and model_version = " + literal(model))
			.update();
		return true;
	}

	/** 인덱스 이름 — 모델 버전의 SHA-256 앞 16 hex */
	static String name(String model) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(model.getBytes(StandardCharsets.UTF_8));
			return PREFIX + HexFormat.of().formatHex(digest).substring(0, 16);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** SQL 문자열 리터럴 — 작은따옴표를 둘로 */
	private static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

}
