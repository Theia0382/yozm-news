package net.yozmnews.user.domain.interest;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetId;
import org.jspecify.annotations.Nullable;

/**
 * 설정 행 aggregate({@code user_settings}) — 사용자마다 하나 — docs/specs/user.md §4 · user-R43 · user-R45 · user-R46
 * <p>
 * 선호 버전(태그 선호 전체가 그 버전의 키다) · 일정 · 관심사 · 고른 모델 · 변화 리포트. 관심 설정의 쓰기 · 조회는 이 행을 잠근다. 바꾸는 메서드는
 * 새 값을 돌려주고 고친 시각을 옮긴다 — 판은 저장소가 올린다.
 */
public final class InterestSettings {

	private final UserId user;

	private final TagSetId preferenceVersion;

	private final DigestSchedule schedule;

	private final InterestText interestText;

	private final @Nullable ModelRef model;

	private final ReportSettings reports;

	private final Instant updatedAt;

	private final long version;

	public InterestSettings(UserId user, TagSetId preferenceVersion, DigestSchedule schedule, InterestText interestText,
			@Nullable ModelRef model, ReportSettings reports, Instant updatedAt, long version) {
		this.user = Objects.requireNonNull(user, "user");
		this.preferenceVersion = Objects.requireNonNull(preferenceVersion, "preferenceVersion");
		this.schedule = Objects.requireNonNull(schedule, "schedule");
		this.interestText = Objects.requireNonNull(interestText, "interestText");
		this.model = model;
		this.reports = Objects.requireNonNull(reports, "reports");
		this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
		this.version = version;
	}

	/** 계정을 만들 때의 설정 행 — 기본값({@link InterestDefaults}) · 고른 모델 없음 · 관심사 없음(user-R43) */
	public static InterestSettings defaults(UserId user, TagSetId activeVersion, Instant now) {
		return new InterestSettings(user, activeVersion,
				new DigestSchedule(InterestDefaults.TIMEZONE, InterestDefaults.DIGEST_TIME), InterestText.NONE, null,
				new ReportSettings(InterestDefaults.WEEKLY_REPORT, InterestDefaults.WEEKLY_REPORT_DAY,
						InterestDefaults.MONTHLY_REPORT),
				now, 0);
	}

	/** 선호 버전을 바꾼다 — 따라가기가 부른다(user-R47) */
	public InterestSettings withPreferenceVersion(TagSetId version, Instant now) {
		return new InterestSettings(this.user, version, this.schedule, this.interestText, this.model, this.reports, now,
				this.version);
	}

	/** 일정 · 관심사 · 리포트를 바꾼다(user-R50) */
	public InterestSettings withDigest(DigestSchedule schedule, InterestText interestText, ReportSettings reports,
			Instant now) {
		return new InterestSettings(this.user, this.preferenceVersion, schedule, interestText, this.model, reports, now,
				this.version);
	}

	/** 고른 모델을 바꾼다(user-R51) */
	public InterestSettings withModel(ModelRef model, Instant now) {
		return new InterestSettings(this.user, this.preferenceVersion, this.schedule, this.interestText,
				Objects.requireNonNull(model, "model"), this.reports, now, this.version);
	}

	public UserId user() {
		return this.user;
	}

	public TagSetId preferenceVersion() {
		return this.preferenceVersion;
	}

	public DigestSchedule schedule() {
		return this.schedule;
	}

	public InterestText interestText() {
		return this.interestText;
	}

	public @Nullable ModelRef model() {
		return this.model;
	}

	public ReportSettings reports() {
		return this.reports;
	}

	public Instant updatedAt() {
		return this.updatedAt;
	}

	/** 저장소의 판 — 읽은 행의 판, 새 행은 0 */
	public long version() {
		return this.version;
	}

	@Override
	public String toString() {
		return "InterestSettings[user=" + this.user.value() + ", preferenceVersion=" + this.preferenceVersion.value()
				+ ", version=" + this.version + "]";
	}

}
