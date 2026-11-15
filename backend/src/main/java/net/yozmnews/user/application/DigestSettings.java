package net.yozmnews.user.application;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.interest.DigestSchedule;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.InterestText;
import net.yozmnews.user.domain.interest.ReportSettings;
import net.yozmnews.user.domain.interest.Timezones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정(시간대 · 생성 시각) · 자유 텍스트 관심사 · 변화 리포트 설정의 조회 · 저장 — docs/specs/user.md §4 · user-R50
 */
@Service
public class DigestSettings {

	private final InterestSettingsLock lock;

	private final InterestSettingsRepository settings;

	DigestSettings(InterestSettingsLock lock, InterestSettingsRepository settings) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	@Transactional
	public DigestSettingsView view(UserId user, Instant now) {
		return DigestSettingsView.of(this.lock.lockFollowed(user, now));
	}

	/**
	 * @param time 생성 시각 — 분 단위(처리기가 {@code HH:mm} 으로 읽는다)
	 * @param interestText 앞뒤 공백을 지우기 전의 관심사
	 */
	@Transactional
	public DigestSettingsOutcome save(UserId user, String timezone, LocalTime time, String interestText,
			ReportSettings reports, Instant now) {
		Optional<ZoneId> zone = Timezones.parse(timezone);
		if (zone.isEmpty()) {
			return new DigestSettingsOutcome.UnknownTimezone();
		}
		Optional<InterestText> text = InterestText.parse(interestText);
		if (text.isEmpty()) {
			return new DigestSettingsOutcome.InterestTextTooLong(InterestText.MAX_CODE_POINTS);
		}
		InterestSettings locked = this.lock.lockFollowed(user, now);
		InterestSettings changed = locked.withDigest(new DigestSchedule(zone.get(), time), text.get(), reports, now);
		this.settings.update(changed);
		return new DigestSettingsOutcome.Saved(DigestSettingsView.of(changed));
	}

	/** 일정 · 관심사 · 리포트 */
	public record DigestSettingsView(DigestSchedule schedule, InterestText interestText, ReportSettings reports) {

		static DigestSettingsView of(InterestSettings settings) {
			return new DigestSettingsView(settings.schedule(), settings.interestText(), settings.reports());
		}

	}

	/** 저장 결과 */
	public sealed interface DigestSettingsOutcome {

		/** 저장했다 */
		record Saved(DigestSettingsView settings) implements DigestSettingsOutcome {
		}

		/** 받지 않는 시간대다 — 바꾸지 않았다 */
		record UnknownTimezone() implements DigestSettingsOutcome {
		}

		/** 관심사가 상한을 넘는다 — 바꾸지 않았다 */
		record InterestTextTooLong(int max) implements DigestSettingsOutcome {
		}

	}

}
