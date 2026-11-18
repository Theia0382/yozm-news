package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.domain.interest.CandidateVolume;
import net.yozmnews.user.domain.interest.DigestVolume;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다이제스트 분량(최대 후보 수의 단계 · 직접 입력 · 큰 슬롯 수 · 요약 길이)의 조회 · 저장 — docs/specs/user.md §4
 * <p>
 * 다른 관심 설정 갈래와 같이 설정 행을 만들고 · 잠그고 · 따라가게 한 뒤 읽거나 쓴다(user-R46). 저장은 통째로 바꾸고 이력이 없다. 범위는 저장할
 * 때만 본다 — 직접 입력한 수를 먼저, 큰 슬롯 수를 다음에 보고 어긋난 첫 칸을 결과로 낸다.
 */
@Service
public class DigestVolumeSettings {

	private final InterestSettingsLock lock;

	private final InterestSettingsRepository settings;

	DigestVolumeSettings(InterestSettingsLock lock, InterestSettingsRepository settings) {
		this.lock = Objects.requireNonNull(lock, "lock");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	@Transactional
	public DigestVolume view(UserId user, Instant now) {
		return this.lock.lockFollowed(user, now).volume();
	}

	/**
	 * @param customCount 직접 입력한 최대 후보 수 — {@link CandidateVolume#CUSTOM} 일 때만 준다
	 * @throws IllegalArgumentException 직접 입력과 직접 입력한 수의 짝이 맞지 않을 때({@link DigestVolume#customCountMatches}) —
	 * 범위보다 먼저 본다. 처리기가 먼저 거른다
	 */
	@Transactional
	public VolumeOutcome save(UserId user, CandidateVolume volume, @Nullable Integer customCount, int bigSlots,
			SummaryLength summaryLength, Instant now) {
		if (!DigestVolume.customCountMatches(volume, customCount)) {
			throw new IllegalArgumentException("직접 입력일 때만 직접 입력한 수가 있다: " + volume + ", " + customCount);
		}
		if (customCount != null && !DigestVolume.countInRange(customCount)) {
			return new VolumeOutcome.CountOutOfRange(DigestVolume.MIN_COUNT, DigestVolume.MAX_COUNT);
		}
		if (!DigestVolume.bigSlotsInRange(bigSlots)) {
			return new VolumeOutcome.BigSlotsOutOfRange(DigestVolume.MIN_BIG_SLOTS, DigestVolume.MAX_BIG_SLOTS);
		}
		DigestVolume requested = new DigestVolume(volume, customCount, bigSlots, summaryLength);
		InterestSettings locked = this.lock.lockFollowed(user, now);
		this.settings.update(locked.withVolume(requested, now));
		return new VolumeOutcome.Saved(requested);
	}

	/** 저장 결과 */
	public sealed interface VolumeOutcome {

		/** 저장했다 */
		record Saved(DigestVolume volume) implements VolumeOutcome {
		}

		/** 직접 입력한 수가 범위 밖이다 — 바꾸지 않았다 */
		record CountOutOfRange(int min, int max) implements VolumeOutcome {
		}

		/** 큰 슬롯 수가 범위 밖이다 — 바꾸지 않았다 */
		record BigSlotsOutOfRange(int min, int max) implements VolumeOutcome {
		}

	}

}
