package net.yozmnews.digest.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestCitation;
import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.SectionKind;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.view.DigestDateRow;
import net.yozmnews.digest.domain.view.DigestDayRepository;
import net.yozmnews.digest.domain.view.DigestDayRow;
import net.yozmnews.digest.domain.view.DigestNeighbors;
import net.yozmnews.digest.domain.view.FailedAttempt;
import net.yozmnews.digest.domain.view.SectionReasons;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link DigestDayRepository} 의 JdbcClient 구현 — docs/specs/digest.md §4 · digest-R38 · R39. 보이는 다이제스트는 {@link ShownDigestSql}
 * 로 정한다.
 */
@Repository
class JdbcDigestDayRepository implements DigestDayRepository {

	private final JdbcClient jdbc;

	JdbcDigestDayRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<DigestDayRow> rowsOn(UserId user, LocalDate date) {
		return this.jdbc.sql("""
				select d.id, d.status, d.period_start, d.period_end, d.provider, d.model, d.candidate_count,
				       d.input_tokens, d.output_tokens, d.error_code, d.completed_at,
				       coalesce(d.id = %s, false) as shown
				from digests d
				where d.user_id = :user and d.digest_date = :date
				order by d.id
				""".formatted(ShownDigestSql.shownIdOf("d")))
			.param("user", user.value())
			.param("date", date)
			.query((rs, n) -> new DigestDayRow(rs.getLong("id"), DigestStatus.valueOf(rs.getString("status")),
					instant(rs, "period_start"), instant(rs, "period_end"), rs.getString("provider"),
					rs.getString("model"), rs.getObject("candidate_count", Integer.class),
					rs.getObject("input_tokens", Integer.class), rs.getObject("output_tokens", Integer.class),
					failure(rs.getString("error_code")), nullableInstant(rs, "completed_at"), rs.getBoolean("shown")))
			.list();
	}

	@Override
	public List<DigestSectionView> sectionsOf(long digestId) {
		Map<Long, SectionRows> sections = new LinkedHashMap<>();
		this.jdbc.sql("""
				select s.id, s.kind, s.title, s.body, i.article_id
				from digest_sections s
				left join digest_items i on i.digest_section_id = s.id
				where s.digest_id = :digest
				order by s.position, i.position
				""").param("digest", digestId).query((ResultSet rs) -> {
			long id = rs.getLong("id");
			SectionRows section = sections.get(id);
			if (section == null) {
				section = new SectionRows(SectionKind.valueOf(rs.getString("kind")), rs.getString("title"),
						rs.getString("body"), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
				sections.put(id, section);
			}
			long article = rs.getLong("article_id");
			if (!rs.wasNull()) {
				section.citations().add(new DigestCitation(new ArticleId(article)));
			}
		});
		this.jdbc.sql("""
				select e.digest_section_id, e.event_id, e.selection_reason
				from digest_section_events e
				join digest_sections s on s.id = e.digest_section_id
				where s.digest_id = :digest
				order by e.digest_section_id, e.position
				""").param("digest", digestId).query((ResultSet rs) -> {
			SectionRows section = sections.get(rs.getLong("digest_section_id"));
			if (section != null) {
				section.reasons().add(SelectionReason.valueOf(rs.getString("selection_reason")));
				section.events().add(new EventId(rs.getLong("event_id")));
			}
		});
		return sections.values()
			.stream()
			.map(section -> new DigestSectionView(section.kind(), section.title(), section.body(),
					SectionReasons.of(section.kind(), section.reasons()), section.citations(), section.events()))
			.toList();
	}

	@Override
	public DigestNeighbors neighbors(UserId user, LocalDate date) {
		return this.jdbc.sql("""
				select (select max(digest_date) from digests where user_id = :user and digest_date < :date) as previous,
				       (select min(digest_date) from digests where user_id = :user and digest_date > :date) as next
				""")
			.param("user", user.value())
			.param("date", date)
			.query((rs, n) -> new DigestNeighbors(rs.getObject("previous", LocalDate.class),
					rs.getObject("next", LocalDate.class)))
			.single();
	}

	@Override
	public List<DigestDateRow> dates(UserId user, @Nullable LocalDate before, int limit) {
		// 날짜마다 가장 큰 ID 의 행 하나 — distinct on 이 날짜 순 · ID 역순의 첫 행을 고른다
		return this.jdbc.sql("""
				select distinct on (d.digest_date) d.digest_date, d.status, %s is not null as shown
				from digests d
				where d.user_id = :user and (cast(:before as date) is null or d.digest_date < cast(:before as date))
				order by d.digest_date desc, d.id desc
				limit :limit
				""".formatted(ShownDigestSql.shownIdOf("d")))
			.param("user", user.value())
			.param("before", before)
			.param("limit", limit)
			.query((rs, n) -> new DigestDateRow(rs.getObject("digest_date", LocalDate.class), rs.getBoolean("shown"),
					DigestStatus.valueOf(rs.getString("status"))))
			.list();
	}

	@Override
	public Optional<FailedAttempt> latestFailed(UserId user) {
		return this.jdbc.sql("""
				select id, digest_date, completed_at from digests where user_id = :user and status = 'FAILED'
				order by id desc limit 1
				""")
			.param("user", user.value())
			.query((rs, n) -> new FailedAttempt(rs.getLong("id"), rs.getObject("digest_date", LocalDate.class),
					instant(rs, "completed_at")))
			.optional();
	}

	@Override
	public OptionalLong latestCoveringId(UserId user) {
		Optional<Long> id = this.jdbc
			.sql("select id from digests where user_id = :user and " + CoveringDigestSql.STATUS
					+ " order by id desc limit 1")
			.param("user", user.value())
			.query((rs, n) -> rs.getLong("id"))
			.optional();
		return id.map(OptionalLong::of).orElseGet(OptionalLong::empty);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

	private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value == null) ? null : value.toInstant();
	}

	private static @Nullable DigestFailure failure(@Nullable String code) {
		return (code == null) ? null : DigestFailure.valueOf(code);
	}

	/** 섹션 한 줄과 모은 인용 */
	private record SectionRows(SectionKind kind, @Nullable String title, @Nullable String body,
			List<DigestCitation> citations, List<SelectionReason> reasons, List<EventId> events) {
	}

}
