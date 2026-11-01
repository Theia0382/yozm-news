package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 출처 이름 기록 저장소 — 실제 PostgreSQL 에서 이름이 다를 때만 쓰는 upsert 를 본다.
 * <p>
 * 지키는 규칙: source-R57
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcSourceAttributionRepository.class })
class JdbcSourceAttributionRepositoryTests {

	private static final Instant FIRST = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant LATER = Instant.parse("2026-11-02T00:00:00Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JdbcSourceAttributionRepository repository;

	@Test
	void recordsTheNameOfASourceWithoutARecord() {
		SourceId source = source("alpha");

		this.repository.record(source, "알파일보", FIRST);

		assertThat(row(source)).containsEntry("name", "알파일보");
		assertThat(recordedAt(source)).isEqualTo(FIRST);
	}

	@Test
	void theSameNameKeepsTheFirstRecordingTime() {
		SourceId source = source("alpha");
		this.repository.record(source, "알파일보", FIRST);

		this.repository.record(source, "알파일보", LATER);

		assertThat(row(source)).containsEntry("name", "알파일보");
		assertThat(recordedAt(source)).isEqualTo(FIRST);
	}

	@Test
	void aChangedNameReplacesTheRecord() {
		SourceId source = source("alpha");
		this.repository.record(source, "알파일보", FIRST);

		this.repository.record(source, "알파신문", LATER);

		assertThat(row(source)).containsEntry("name", "알파신문");
		assertThat(recordedAt(source)).isEqualTo(LATER);
	}

	@Test
	void findsTheRecordedNameOfEachSource() {
		SourceId alpha = source("alpha");
		SourceId beta = source("beta");
		source("gamma");
		this.repository.record(alpha, "알파일보", FIRST);
		this.repository.record(beta, "베타뉴스", FIRST);

		assertThat(this.repository.findAll()).containsExactly(entry(alpha, "알파일보"), entry(beta, "베타뉴스"));
	}

	private SourceId source(String presetKey) {
		return new SourceId(this.jdbc
			.sql("insert into sources (preset_key, enabled, version, created_at) values (:key, false, 0, now()) "
					+ "returning id")
			.param("key", presetKey)
			.query(Long.class)
			.single());
	}

	private Map<String, Object> row(SourceId source) {
		return this.jdbc.sql("select name from source_attributions where source_id = :source")
			.param("source", source.value())
			.query()
			.singleRow();
	}

	private Instant recordedAt(SourceId source) {
		return this.jdbc.sql("select recorded_at from source_attributions where source_id = :source")
			.param("source", source.value())
			.query((rs, rowNum) -> rs.getObject("recorded_at", OffsetDateTime.class).toInstant())
			.single();
	}

}
