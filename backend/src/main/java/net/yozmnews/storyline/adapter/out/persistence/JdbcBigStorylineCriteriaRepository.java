package net.yozmnews.storyline.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.storyline.domain.big.BigStorylineCriteriaRepository;
import net.yozmnews.platform.ChangeActor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link BigStorylineCriteriaRepository} 의 JdbcClient 구현 — docs/specs/storyline.md §4.
 * <p>
 * 지금 값은 ID 1 인 한 행이다. 중요도는 {@code NUMERIC(3, 2)} 를 {@link BigDecimal} 로 읽고 쓴다.
 */
@Repository
class JdbcBigStorylineCriteriaRepository implements BigStorylineCriteriaRepository {

	private final JdbcClient jdbc;

	JdbcBigStorylineCriteriaRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean seedIfAbsent(BigStorylineCriteria criteria, Instant now) {
		int inserted = this.jdbc.sql("""
				insert into storyline_big_criteria (id, min_sources, min_importance, changed_at)
				values (1, :sources, :importance, :now)
				on conflict (id) do nothing
				""")
			.param("sources", criteria.minSources())
			.param("importance", criteria.minImportance())
			.param("now", timestamp(now))
			.update();
		if (inserted == 0) {
			return false;
		}
		history(null, criteria, ChangeActor.SYSTEM, now);
		return true;
	}

	@Override
	public Optional<BigStorylineCriteria> current() {
		return this.jdbc.sql("select min_sources, min_importance from storyline_big_criteria where id = 1")
			.query((rs, row) -> criteria(rs, ""))
			.optional();
	}

	@Override
	public BigStorylineCriteria lockCurrent() {
		return this.jdbc.sql("select min_sources, min_importance from storyline_big_criteria where id = 1 for update")
			.query((rs, row) -> criteria(rs, ""))
			.optional()
			.orElseThrow(() -> new IllegalStateException("큰 판정 기준의 처음 값이 없다"));
	}

	@Override
	public void change(BigStorylineCriteria previous, BigStorylineCriteria next, ChangeActor actor, Instant now) {
		this.jdbc.sql("""
				update storyline_big_criteria set min_sources = :sources, min_importance = :importance, changed_at = :now
				where id = 1
				""")
			.param("sources", next.minSources())
			.param("importance", next.minImportance())
			.param("now", timestamp(now))
			.update();
		history(previous, next, actor, now);
	}

	@Override
	public List<BigStorylineCriteriaChange> changes() {
		return this.jdbc.sql("""
				select previous_min_sources, previous_min_importance, min_sources, min_importance, actor, changed_at
				from storyline_big_criteria_changes
				order by changed_at desc, id desc
				""").query((rs, row) -> {
					BigStorylineCriteria previous = rs.getObject("previous_min_sources") == null ? null
							: criteria(rs, "previous_");
					return new BigStorylineCriteriaChange(previous, criteria(rs, ""), ChangeActor.valueOf(rs.getString("actor")),
							rs.getObject("changed_at", OffsetDateTime.class).toInstant());
				}).list();
	}

	private void history(@Nullable BigStorylineCriteria previous, BigStorylineCriteria next, ChangeActor actor, Instant now) {
		this.jdbc.sql("""
				insert into storyline_big_criteria_changes (previous_min_sources, previous_min_importance, min_sources,
				                                        min_importance, actor, changed_at)
				values (:previousSources, :previousImportance, :sources, :importance, :actor, :now)
				""")
			.param("previousSources", previous == null ? null : previous.minSources(), Types.INTEGER)
			.param("previousImportance", previous == null ? null : previous.minImportance(), Types.NUMERIC)
			.param("sources", next.minSources())
			.param("importance", next.minImportance())
			.param("actor", actor.name())
			.param("now", timestamp(now))
			.update();
	}

	private static BigStorylineCriteria criteria(ResultSet rs, String prefix) throws SQLException {
		return new BigStorylineCriteria(rs.getInt(prefix + "min_sources"), rs.getBigDecimal(prefix + "min_importance"));
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
