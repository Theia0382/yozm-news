package net.yozmnews.user.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import net.yozmnews.user.SummaryLength;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 분량 — 단계의 값, 직접 입력의 짝, 범위 경계(직접 입력한 수 10 ~ 100 · 큰 슬롯 수 0 ~ 10), 기본값(보통 · 3 · 요약 길이 보통)
 */
class DigestVolumeTests {

	private static final SummaryLength LENGTH = SummaryLength.NORMAL;

	@Test
	void eachPresetHasItsCountAndCustomTakesTheUsersCount() {
		assertThat(new DigestVolume(CandidateVolume.FEW, null, 3, LENGTH).maxCandidates()).isEqualTo(20);
		assertThat(new DigestVolume(CandidateVolume.NORMAL, null, 3, LENGTH).maxCandidates()).isEqualTo(40);
		assertThat(new DigestVolume(CandidateVolume.MANY, null, 3, LENGTH).maxCandidates()).isEqualTo(60);
		assertThat(new DigestVolume(CandidateVolume.CUSTOM, 45, 3, LENGTH).maxCandidates()).isEqualTo(45);
		assertThatIllegalStateException().isThrownBy(CandidateVolume.CUSTOM::count);
	}

	@Test
	void theDefaultIsNormalWithThreeBigSlotsAndNormalLength() {
		assertThat(DigestVolume.defaults())
			.isEqualTo(new DigestVolume(CandidateVolume.NORMAL, null, 3, SummaryLength.NORMAL));
		assertThat(DigestVolume.defaults().maxCandidates()).isEqualTo(40);
	}

	@Test
	void theCustomCountComesOnlyWithCustom() {
		assertThat(DigestVolume.customCountMatches(CandidateVolume.CUSTOM, 45)).isTrue();
		assertThat(DigestVolume.customCountMatches(CandidateVolume.CUSTOM, null)).isFalse();
		assertThat(DigestVolume.customCountMatches(CandidateVolume.FEW, null)).isTrue();
		assertThat(DigestVolume.customCountMatches(CandidateVolume.FEW, 45)).isFalse();
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.NORMAL, 45, 3, LENGTH));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.CUSTOM, null, 3, LENGTH));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.CUSTOM, 0, 3, LENGTH));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new DigestVolume(CandidateVolume.NORMAL, null, -1, LENGTH));
	}

	@Test
	void theSummaryLengthIsRequired() {
		assertThatNullPointerException().isThrownBy(() -> new DigestVolume(CandidateVolume.NORMAL, null, 3, null));
		assertThat(new DigestVolume(CandidateVolume.NORMAL, null, 3, SummaryLength.DETAILED).summaryLength())
			.isEqualTo(SummaryLength.DETAILED);
	}

	@Test
	void theConstructorReadsAStoredValueOutsideTodaysRange() {
		assertThat(new DigestVolume(CandidateVolume.CUSTOM, 200, 20, LENGTH).maxCandidates()).isEqualTo(200);
	}

	@Test
	void rangesIncludeBothEnds() {
		assertThat(DigestVolume.countInRange(9)).isFalse();
		assertThat(DigestVolume.countInRange(10)).isTrue();
		assertThat(DigestVolume.countInRange(100)).isTrue();
		assertThat(DigestVolume.countInRange(101)).isFalse();
		assertThat(DigestVolume.bigSlotsInRange(-1)).isFalse();
		assertThat(DigestVolume.bigSlotsInRange(0)).isTrue();
		assertThat(DigestVolume.bigSlotsInRange(10)).isTrue();
		assertThat(DigestVolume.bigSlotsInRange(11)).isFalse();
	}

}
