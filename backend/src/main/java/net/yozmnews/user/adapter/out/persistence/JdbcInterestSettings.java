package net.yozmnews.user.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.domain.account.AccountStatus;
import net.yozmnews.user.domain.interest.CandidateVolume;
import net.yozmnews.user.domain.interest.DigestSchedule;
import net.yozmnews.user.domain.interest.DigestVolume;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.InterestText;
import net.yozmnews.user.domain.interest.ModelRef;
import net.yozmnews.user.domain.interest.ReportSettings;
import net.yozmnews.user.domain.interest.ScheduledUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link InterestSettingsRepository} 의 PostgreSQL 구현 — {@code user_settings}. docs/specs/user.md §4 · §6
 */
@Repository
class JdbcInterestSettings implements InterestSettingsRepository {

	private static final String COLUMNS = """
			user_id, tag_set_id, timezone, digest_time, interest_text, llm_provider, llm_model, reasoning_effort,
			weekly_report, weekly_report_day, monthly_report, candidate_volume, candidate_count, big_slots,
			summary_length, updated_at, version""";

	private final JdbcClient jdbc;

	JdbcInterestSettings(JdbcClient jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
	}

	@Override
	public Optional<InterestSettings> lockByUser(UserId user) {
		return this.jdbc.sql("select " + COLUMNS + " from user_settings where user_id = :user for update")
			.param("user", user.value())
			.query(JdbcInterestSettings::row)
			.optional();
	}

	@Override
	public Optional<InterestSettings> findByUser(UserId user) {
		return this.jdbc.sql("select " + COLUMNS + " from user_settings where user_id = :user")
			.param("user", user.value())
			.query(JdbcInterestSettings::row)
			.optional();
	}

	@Override
	public boolean insertIfAbsent(InterestSettings settings) {
		return this.jdbc.sql("insert into user_settings (" + COLUMNS + """
				) values (:user, :tagSet, :timezone, :digestTime, :interestText, :provider, :model, :effort, :weekly,
				  :weeklyDay, :monthly, :candidateVolume, :candidateCount, :bigSlots, :summaryLength, :updatedAt, 0)
				on conflict (user_id) do nothing
				""")
			.paramSource(params(settings))
			.update() == 1;
	}

	@Override
	public void update(InterestSettings settings) {
		MapSqlParameterSource params = params(settings).addValue("version", settings.version());
		int updated = this.jdbc.sql("""
				update user_settings set tag_set_id = :tagSet, timezone = :timezone, digest_time = :digestTime,
				  interest_text = :interestText, llm_provider = :provider, llm_model = :model, reasoning_effort = :effort,
				  weekly_report = :weekly, weekly_report_day = :weeklyDay, monthly_report = :monthly,
				  candidate_volume = :candidateVolume, candidate_count = :candidateCount, big_slots = :bigSlots,
				  summary_length = :summaryLength, updated_at = :updatedAt, version = version + 1
				where user_id = :user and version = :version
				""").paramSource(params).update();
		if (updated != 1) {
			throw new IllegalStateException("읽은 판의 설정 행이 없다: " + settings);
		}
	}

	@Override
	public List<UserId> withoutSettings(long afterUserId, int limit) {
		return this.jdbc.sql("""
				select u.id from users u
				where u.id > :after and not exists (select 1 from user_settings s where s.user_id = u.id)
				order by u.id limit :limit
				""").param("after", afterUserId).param("limit", limit).query((rs, n) -> new UserId(rs.getLong(1))).list();
	}

	@Override
	public List<UserId> withPreferenceVersionOtherThan(TagSetId active, long afterUserId, int limit) {
		return this.jdbc.sql("""
				select user_id from user_settings
				where tag_set_id <> :active and user_id > :after
				order by user_id limit :limit
				""")
			.param("active", active.value())
			.param("after", afterUserId)
			.param("limit", limit)
			.query((rs, n) -> new UserId(rs.getLong(1)))
			.list();
	}

	@Override
	public List<ScheduledUser> activeSchedules(long afterUserId, int limit) {
		return this.jdbc.sql("""
				select s.user_id, s.timezone, s.digest_time from user_settings s
				join users u on u.id = s.user_id
				where u.status = :active and s.user_id > :after
				order by s.user_id limit :limit
				""")
			.param("active", AccountStatus.ACTIVE.name())
			.param("after", afterUserId)
			.param("limit", limit)
			.query((rs, n) -> new ScheduledUser(new UserId(rs.getLong("user_id")), schedule(rs)))
			.list();
	}

	private static DigestSchedule schedule(ResultSet rs) throws SQLException {
		return new DigestSchedule(ZoneId.of(rs.getString("timezone")), rs.getObject("digest_time", LocalTime.class));
	}

	private static InterestSettings row(ResultSet rs, int rowNum) throws SQLException {
		String provider = rs.getString("llm_provider");
		ModelRef model = null;
		if (provider != null) {
			model = new ModelRef(Provider.ofKey(provider)
				.orElseThrow(() -> new IllegalStateException("모르는 공급자 키가 저장돼 있다: " + provider)),
					Objects.requireNonNull(rs.getString("llm_model"), "llm_model"));
		}
		return new InterestSettings(new UserId(rs.getLong("user_id")), new TagSetId(rs.getLong("tag_set_id")),
				schedule(rs), new InterestText(rs.getString("interest_text")), model,
				ReasoningEffort.valueOf(rs.getString("reasoning_effort")),
				new ReportSettings(rs.getBoolean("weekly_report"), DayOfWeek.of(rs.getInt("weekly_report_day")),
						rs.getBoolean("monthly_report")),
				new DigestVolume(CandidateVolume.valueOf(rs.getString("candidate_volume")),
						rs.getObject("candidate_count", Integer.class), rs.getInt("big_slots"),
						SummaryLength.valueOf(rs.getString("summary_length"))),
				rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
	}

	private static MapSqlParameterSource params(InterestSettings settings) {
		ModelRef model = settings.model();
		return new MapSqlParameterSource().addValue("user", settings.user().value())
			.addValue("tagSet", settings.preferenceVersion().value())
			.addValue("timezone", settings.schedule().timezone().getId())
			.addValue("digestTime", settings.schedule().time())
			.addValue("interestText", settings.interestText().value())
			.addValue("provider", model == null ? null : model.provider().key())
			.addValue("model", model == null ? null : model.modelId())
			.addValue("effort", settings.effort().name())
			.addValue("weekly", settings.reports().weekly())
			.addValue("weeklyDay", settings.reports().weeklyDay().getValue())
			.addValue("monthly", settings.reports().monthly())
			.addValue("candidateVolume", settings.volume().volume().name())
			.addValue("candidateCount", settings.volume().customCount())
			.addValue("bigSlots", settings.volume().bigSlots())
			.addValue("summaryLength", settings.volume().summaryLength().name())
			.addValue("updatedAt", Timestamp.from(settings.updatedAt()));
	}

}
