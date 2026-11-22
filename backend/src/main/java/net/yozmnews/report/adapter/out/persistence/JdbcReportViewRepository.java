package net.yozmnews.report.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportCitation;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.ReportSummary;
import net.yozmnews.report.domain.view.ReportRow;
import net.yozmnews.report.domain.view.ReportViewRepository;
import net.yozmnews.report.domain.view.StoredSection;
import net.yozmnews.storyline.StorylineId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 리포트 조회 저장소의 JdbcClient 구현 — 질의마다 사용자 조건을 건다 · docs/specs/report.md §4 · report-R11
 */
@Repository
class JdbcReportViewRepository implements ReportViewRepository {

	private static final String ROW_COLUMNS = """
			id, period_kind, period_start, period_end, status, error_code, created_at, completed_at, provider, model,
			input_tokens, output_tokens, stats_snapshot::text as stats_snapshot""";

	private final JdbcClient jdbc;

	JdbcReportViewRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ReportRow> page(UserId user, @Nullable ReportId before, int limit) {
		Objects.requireNonNull(user, "user");
		String sql = "select " + ROW_COLUMNS + " from reports where user_id = :user"
				+ ((before == null) ? "" : " and id < :before") + " order by id desc limit :limit";
		JdbcClient.StatementSpec statement = this.jdbc.sql(sql).param("user", user.value()).param("limit", limit);
		if (before != null) {
			statement = statement.param("before", before.value());
		}
		return statement.query(JdbcReportViewRepository::row).list();
	}

	@Override
	public Optional<ReportRow> find(UserId user, ReportId id) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(id, "id");
		return this.jdbc.sql("select " + ROW_COLUMNS + " from reports where id = :id and user_id = :user")
			.param("id", id.value())
			.param("user", user.value())
			.query(JdbcReportViewRepository::row)
			.optional();
	}

	@Override
	public List<StoredSection> sections(UserId user, ReportId id) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(id, "id");
		Map<Long, Section> sections = new LinkedHashMap<>();
		this.jdbc.sql("""
				select s.id, s.kind, s.title, s.body, i.storyline_id, i.event_id
				from report_sections s
				join reports r on r.id = s.report_id
				left join report_items i on i.report_section_id = s.id
				where r.id = :id and r.user_id = :user
				order by s.position, i.position""")
			.param("id", id.value())
			.param("user", user.value())
			.query((rs, n) -> {
				Section section = sections.computeIfAbsent(rs.getLong("id"),
						key -> new Section(kind(rs), string(rs, "title"), string(rs, "body"), new ArrayList<>()));
				long storyline = rs.getLong("storyline_id");
				if (!rs.wasNull()) {
					section.citations().add(new ReportCitation.Storyline(new StorylineId(storyline)));
				}
				long event = rs.getLong("event_id");
				if (!rs.wasNull()) {
					section.citations().add(new ReportCitation.Event(new EventId(event)));
				}
				return section;
			})
			.list();
		return sections.values()
			.stream()
			.map(section -> new StoredSection(section.kind(), section.title(),
					Objects.requireNonNull(section.body(), "body"), section.citations()))
			.toList();
	}

	private static ReportRow row(ResultSet rs, int rowNum) throws SQLException {
		String failure = rs.getString("error_code");
		ReportSummary summary = new ReportSummary(new ReportId(rs.getLong("id")),
				ReportPeriodKind.valueOf(rs.getString("period_kind")), instant(rs, "period_start"),
				instant(rs, "period_end"), ReportStatus.valueOf(rs.getString("status")),
				(failure == null) ? null : ReportFailure.valueOf(failure), instant(rs, "created_at"),
				optionalInstant(rs, "completed_at"));
		return new ReportRow(summary, rs.getString("provider"), rs.getString("model"),
				integer(rs, "input_tokens"), integer(rs, "output_tokens"), rs.getString("stats_snapshot"));
	}

	private static ReportSectionKind kind(ResultSet rs) {
		try {
			return ReportSectionKind.valueOf(rs.getString("kind"));
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static @Nullable String string(ResultSet rs, String column) {
		try {
			return rs.getString(column);
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return Objects.requireNonNull(rs.getObject(column, OffsetDateTime.class), column).toInstant();
	}

	private static @Nullable Instant optionalInstant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value == null) ? null : value.toInstant();
	}

	private static @Nullable Integer integer(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	/** 읽는 중인 섹션 — 인용을 위치 순으로 모은다 */
	private record Section(ReportSectionKind kind, @Nullable String title, @Nullable String body,
			List<ReportCitation> citations) {
	}

}
