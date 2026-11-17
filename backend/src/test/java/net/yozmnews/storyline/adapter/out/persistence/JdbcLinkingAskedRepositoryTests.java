package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.AskedRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 물은 기록 — 붙이기 띠(사건, 스토리라인) · 짝 띠(사건, 이웃 사건)마다 한 행, 다시 쓰면 덮어쓴다. 확률 없는 기록은 그대로 읽힌다. 합쳐질
 * 스토리라인 · 지워진 사건의 기록을 지우고, 지운 스토리라인의 기록은 FK 로 함께 지워진다.
 * <p>
 * 지키는 규칙: storyline-R36
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcLinkingAskedRepository.class })
class JdbcLinkingAskedRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	@Autowired
	JdbcLinkingAskedRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void recordsAreReadBackAndOverwritten() {
		long s1 = storyline();
		long s2 = storyline();
		this.repository.recordLink(9101, Map.of(s1, new AskedRecord(new Asked(0.71, 77L), 1, 2), s2,
				new AskedRecord(Asked.UNANSWERED, 1, 3)), NOW);

		assertThat(this.repository.ofLink(9101, List.of(s1, s2, 999_999L))).containsOnly(
				Map.entry(s1, new AskedRecord(new Asked(0.71, 77L), 1, 2)),
				Map.entry(s2, new AskedRecord(Asked.UNANSWERED, 1, 3)));

		this.repository.recordLink(9101, Map.of(s1, new AskedRecord(new Asked(0.2, 78L), 2, 4)), NOW.plusSeconds(60));
		assertThat(this.repository.ofLink(9101, List.of(s1))).containsOnly(
				Map.entry(s1, new AskedRecord(new Asked(0.2, 78L), 2, 4)));
		assertThat(this.repository.ofLink(9101, List.of())).isEmpty();
	}

	@Test
	void pairRecordsHaveADirection() {
		this.repository.recordPair(9201, Map.of(9202L, new AskedRecord(new Asked(0.9, 88L), 1, 3)), NOW);

		assertThat(this.repository.ofPair(9201, List.of(9202L))).containsOnly(
				Map.entry(9202L, new AskedRecord(new Asked(0.9, 88L), 1, 3)));
		assertThat(this.repository.ofPair(9202, List.of(9201L))).isEmpty();
	}

	@Test
	void forgettingAnEventRemovesItsRecordsOnBothSidesOfBothTables() {
		long s1 = storyline();
		this.repository.recordLink(9301, Map.of(s1, new AskedRecord(new Asked(0.71, 77L), 1, 2)), NOW);
		this.repository.recordLink(9302, Map.of(s1, new AskedRecord(new Asked(0.71, 77L), 1, 2)), NOW);
		this.repository.recordPair(9301, Map.of(9303L, new AskedRecord(new Asked(0.9, 88L), 1, 1)), NOW);
		this.repository.recordPair(9304, Map.of(9301L, new AskedRecord(new Asked(0.9, 89L), 1, 1)), NOW);
		this.repository.recordPair(9304, Map.of(9305L, new AskedRecord(new Asked(0.9, 90L), 1, 1)), NOW);

		this.repository.forgetEvent(9301);

		assertThat(this.repository.ofLink(9301, List.of(s1))).isEmpty();
		assertThat(this.repository.ofLink(9302, List.of(s1))).containsOnlyKeys(s1);
		assertThat(this.repository.ofPair(9301, List.of(9303L))).isEmpty();
		assertThat(this.repository.ofPair(9304, List.of(9301L, 9305L))).containsOnlyKeys(9305L);
	}

	@Test
	void forgettingAStorylineOrDeletingItRemovesItsRecords() {
		long kept = storyline();
		long merged = storyline();
		long deleted = storyline();
		this.repository.recordLink(9401, Map.of(kept, new AskedRecord(new Asked(0.71, 77L), 1, 2), merged,
				new AskedRecord(new Asked(0.6, 78L), 1, 2), deleted, new AskedRecord(new Asked(0.5, 79L), 1, 2)), NOW);

		this.repository.forgetStoryline(merged);
		this.jdbc.sql("delete from storylines where id = :id").param("id", deleted).update();

		assertThat(this.repository.ofLink(9401, List.of(kept, merged, deleted))).containsOnlyKeys(kept);
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('asked-m', cast('[1,0]' as halfvec), 1, now(), now(), 1, now(), now())
				returning id
				""").query(Long.class).single();
	}

}
