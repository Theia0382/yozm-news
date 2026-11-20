package net.yozmnews.user.domain.interest;

import java.util.Objects;

import net.yozmnews.user.SummaryLength;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 분량 설정 — 최대 후보 수의 단계(또는 직접 입력한 수) · 큰 슬롯 수 · 요약 길이. 범위의 정본 — docs/specs/user.md §5
 * <p>
 * 표준 생성자는 구조만 검사한다 — 범위는 저장할 때만 본다({@link #countInRange} · {@link #bigSlotsInRange}). 범위 상수를 나중에 좁혀도
 * 이미 저장된 행을 읽을 수 있어야 하고, 그 행은 사용자가 다시 저장할 때 새 범위를 따른다. 범위 값은 측정 전 목표다.
 * @param volume 최대 후보 수의 단계
 * @param customCount 직접 입력한 최대 후보 수 — {@link CandidateVolume#CUSTOM} 일 때만 있다
 * @param bigSlots 큰 슬롯 수 — 0 이면 큰 슬롯을 넣지 않는다
 * @param summaryLength 요약 길이 — 단계의 뜻은 다이제스트의 지시가 정한다
 */
public record DigestVolume(CandidateVolume volume, @Nullable Integer customCount, int bigSlots,
		SummaryLength summaryLength) {

	/** 직접 입력한 최대 후보 수의 하한 */
	public static final int MIN_COUNT = 10;

	/**
	 * 직접 입력한 최대 후보 수의 상한 — 최대 후보 수는 사건 수다. 다이제스트 하나의 항목 수 위쪽(이 값)을 고쳐 누르는 사용을 피드백 요청
	 * 제한({@code RequestLimits})이 덮어야 한다 — 이 값을 올리면 그 값을 함께 본다
	 */
	public static final int MAX_COUNT = 150;

	/** 큰 슬롯 수의 하한 — 0 은 큰 슬롯을 끈다 */
	public static final int MIN_BIG_SLOTS = 0;

	/** 큰 슬롯 수의 상한 */
	public static final int MAX_BIG_SLOTS = 10;

	/**
	 * @throws IllegalArgumentException 직접 입력과 직접 입력한 수의 짝이 맞지 않거나, 직접 입력한 수가 1 보다 작거나, 큰 슬롯 수가 음수일 때
	 */
	public DigestVolume {
		Objects.requireNonNull(volume, "volume");
		Objects.requireNonNull(summaryLength, "summaryLength");
		if (!customCountMatches(volume, customCount)) {
			throw new IllegalArgumentException("직접 입력일 때만 직접 입력한 수가 있다: " + volume + ", " + customCount);
		}
		if (customCount != null && customCount < 1) {
			throw new IllegalArgumentException("직접 입력한 수가 1 보다 작다: " + customCount);
		}
		if (bigSlots < 0) {
			throw new IllegalArgumentException("큰 슬롯 수가 음수다: " + bigSlots);
		}
	}

	/** 직접 입력과 직접 입력한 수의 짝이 맞는가 — 직접 입력({@link CandidateVolume#CUSTOM})일 때만 수가 있다 */
	public static boolean customCountMatches(CandidateVolume volume, @Nullable Integer customCount) {
		return volume.preset() == (customCount == null);
	}

	/** 계정을 만들 때의 분량 — {@link InterestDefaults} */
	public static DigestVolume defaults() {
		return new DigestVolume(InterestDefaults.CANDIDATE_VOLUME, null, InterestDefaults.BIG_SLOTS,
				InterestDefaults.SUMMARY_LENGTH);
	}

	/** 최대 후보 수 — 단계의 값 또는 직접 입력한 수 */
	public int maxCandidates() {
		return this.volume.preset() ? this.volume.count() : Objects.requireNonNull(this.customCount);
	}

	/** 직접 입력한 최대 후보 수로 받는 값인가 — {@link #MIN_COUNT} ~ {@link #MAX_COUNT}, 경계 포함 */
	public static boolean countInRange(int count) {
		return count >= MIN_COUNT && count <= MAX_COUNT;
	}

	/** 큰 슬롯 수로 받는 값인가 — {@link #MIN_BIG_SLOTS} ~ {@link #MAX_BIG_SLOTS}, 경계 포함 */
	public static boolean bigSlotsInRange(int bigSlots) {
		return bigSlots >= MIN_BIG_SLOTS && bigSlots <= MAX_BIG_SLOTS;
	}

}
