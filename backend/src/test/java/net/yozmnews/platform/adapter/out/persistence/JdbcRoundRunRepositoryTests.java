package net.yozmnews.platform.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.RoundEnd;
import net.yozmnews.platform.RoundSummary;
import net.yozmnews.platform.domain.RoundRules;
import net.yozmnews.platform.domain.RoundRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 회차 기록 저장소 — Flyway 가 적용된 실제 PostgreSQL 에서 쓰고 · 읽고 · 지운다.
 * <p>
 * 지키는 규칙: platform-R132 · platform-R133 · platform-R134
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcRoundRunRepository.class })
class JdbcRoundRunRepositoryTests {

	static final Instant NOON = Instant.parse("2026-11-26T12:00:00Z");

	@Autowired
	JdbcRoundRunRepository runs;

	@Autowired
	JdbcClient jdbc;

	@Test
	void noRecordIsZeroWithoutALastRound() {
		assertThat(this.runs.recent("storyline-linking", NOON)).isEqualTo(new RoundSummary(0, 0, null));
	}

	@Test
	void recentSumsTheRunsThatEndedInTheWindowAndNamesTheLatest() {
		this.runs.insert(run("storyline-linking", "11:00:00", "11:00:30", 1, 9, RoundEnd.DRAINED));
		this.runs.insert(run("storyline-linking", "11:59:00", "12:00:00", 2, 40, RoundEnd.DRAINED));
		this.runs.insert(run("storyline-linking", "12:01:00", "12:06:10", 11, 352, RoundEnd.BUDGET));
		this.runs.insert(run("storyline-relation", "12:01:00", "12:01:01", 1, 4, RoundEnd.DRAINED));

		// 창의 시작(12:00:00)에 끝난 회차는 창에 든다
		assertThat(this.runs.recent("storyline-linking", NOON)).isEqualTo(new RoundSummary(392, 2,
				new RoundSummary.LastRound(at("12:01:00"), at("12:06:10"), 11, 352, RoundEnd.BUDGET)));
	}

	@Test
	void theLatestRunIsNamedEvenWhenItEndedBeforeTheWindow() {
		this.runs.insert(run("event-merge", "09:00:00", "09:00:02", 1, 0, RoundEnd.DRAINED));

		assertThat(this.runs.recent("event-merge", NOON)).isEqualTo(new RoundSummary(0, 0,
				new RoundSummary.LastRound(at("09:00:00"), at("09:00:02"), 1, 0, RoundEnd.DRAINED)));
	}

	@Test
	void deletesOnlyRunsThatEndedBeforeTheCut() {
		this.runs.insert(run("event-features", "11:00:00", "11:59:59", 1, 1, RoundEnd.DRAINED));
		this.runs.insert(run("event-features", "11:00:00", "12:00:00", 1, 2, RoundEnd.DRAINED));

		assertThat(this.runs.deleteEndedBefore(NOON)).isEqualTo(1);
		assertThat(this.jdbc.sql("select handled from round_runs").query(Integer.class).list()).containsExactly(2);
	}

	@ParameterizedTest
	@EnumSource(RoundEnd.class)
	void everyReasonIsStorable(RoundEnd end) {
		this.runs.insert(run("tag-evaluation", "12:00:00", "12:00:01", 1, 1, end));

		assertThat(this.runs.recent("tag-evaluation", NOON).last()).extracting(RoundSummary.LastRound::end).isEqualTo(end);
	}

	@ParameterizedTest
	@ValueSource(strings = { "a", "event-features", "storyline-linking", "", "Event", "event_merge", "event--merge",
			"-event", "event-", "event1", "이벤트", "event\n" })
	void theSchemaAcceptsExactlyTheNamesTheCodeAccepts(String round) {
		boolean codeAccepts = RoundRules.NAME.matcher(round).matches();
		Runnable insert = () -> this.jdbc.sql("""
				insert into round_runs (round, started_at, ended_at, batches, handled, ended_by)
				values (:round, :at, :at, 0, 0, 'DRAINED')
				""").param("round", round).param("at", NOON.atOffset(ZoneOffset.UTC)).update();
		if (codeAccepts) {
			insert.run();
		}
		else {
			assertThatThrownBy(insert::run).isInstanceOf(DataIntegrityViolationException.class);
		}
		assertThat(List.of("a", "event-features", "storyline-linking").contains(round)).isEqualTo(codeAccepts);
	}

	@Test
	void theCodePatternIsTheSchemaPattern() {
		String check = this.jdbc.sql("""
				select pg_get_constraintdef(c.oid) from pg_constraint c
				where c.conrelid = 'round_runs'::regclass and pg_get_constraintdef(c.oid) like '%round ~%'
				""").query(String.class).single();
		assertThat(check).contains("'^" + RoundRules.NAME.pattern() + "$'");
		assertThat(RoundRules.NAME.pattern()).isEqualTo(Pattern.compile("[a-z]+(-[a-z]+)*").pattern());
	}

	private static RoundRun run(String round, String start, String end, int batches, int handled, RoundEnd ended) {
		return new RoundRun(round, at(start), at(end), batches, handled, ended);
	}

	private static Instant at(String time) {
		return Instant.parse("2026-11-26T" + time + "Z");
	}

}
