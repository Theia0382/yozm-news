package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.EventJudgementId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 병합 판정 기록 — 순서 없는 쌍 하나에 한 행, 먼저 남긴 확률이 남는다.
 * <p>
 * 지키는 규칙: event-R13
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcMergeJudgementRepository.class })
class JdbcMergeJudgementRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

	@Autowired
	JdbcMergeJudgementRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void aPairIsOneRowReadFromEitherSide() {
		this.repository.save(20, Map.of(10L, 0.8, 30L, 0.3), new EventJudgementId(5), NOW);

		assertThat(this.repository.find(20, List.of(10L, 30L, 40L))).containsExactlyInAnyOrderEntriesOf(
				Map.of(10L, 0.8, 30L, 0.3));
		assertThat(this.repository.find(10, List.of(20L))).containsExactlyEntriesOf(Map.of(20L, 0.8));
		assertThat(this.repository.find(30, List.of(20L, 10L))).containsExactlyEntriesOf(Map.of(20L, 0.3));
		assertThat(this.repository.find(20, List.of())).isEmpty();
		assertThat(this.jdbc.sql("select event_low, event_high from event_merge_judgements order by event_low")
			.query()
			.listOfRows()).containsExactly(Map.of("event_low", 10L, "event_high", 20L),
					Map.of("event_low", 20L, "event_high", 30L));
	}

	@Test
	void theFirstProbabilityOfAPairStays() {
		this.repository.save(20, Map.of(10L, 0.8), new EventJudgementId(5), NOW);
		this.repository.save(10, Map.of(20L, 0.1), new EventJudgementId(6), NOW.plusSeconds(60));

		assertThat(this.repository.find(20, List.of(10L))).containsExactlyEntriesOf(Map.of(10L, 0.8));
		assertThat(judgementOf(10, 20)).isEqualTo(5L);
	}

	@Test
	void eachNewPairKeepsTheJudgementThatGaveItsProbability() {
		this.repository.save(20, Map.of(10L, 0.8, 30L, 0.3), new EventJudgementId(5), NOW);
		this.repository.save(40, Map.of(20L, 0.6), null, NOW);

		assertThat(judgementOf(10, 20)).isEqualTo(5L);
		assertThat(judgementOf(20, 30)).isEqualTo(5L);
		assertThat(judgementOf(20, 40)).isNull();
	}

	private @Nullable Long judgementOf(long low, long high) {
		return (Long) this.jdbc.sql("select judgement_id from event_merge_judgements where event_low = :low and event_high = :high")
			.param("low", low)
			.param("high", high)
			.query()
			.singleRow()
			.get("judgement_id");
	}

}
