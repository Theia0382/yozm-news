package net.yozmnews.user.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

/**
 * 다이제스트 분량 — 단계의 값, 직접 입력의 짝, 범위 경계(직접 입력한 수 10 ~ 150 · 큰 슬롯 수 0 ~ 10), 기본값(보통 · 3)
 */
class DigestVolumeTests {

	@Test
	void eachPresetHasItsCountAndCustomTakesTheUsersCount() {
		assertThat(new DigestVolume(CandidateVolume.FEW, null, 3).maxCandidates()).isEqualTo(30);
		assertThat(new DigestVolume(CandidateVolume.NORMAL, null, 3).maxCandidates()).isEqualTo(60);
		assertThat(new DigestVolume(CandidateVolume.MANY, null, 3).maxCandidates()).isEqualTo(90);
		assertThat(new DigestVolume(CandidateVolume.CUSTOM, 45, 3).maxCandidates()).isEqualTo(45);
		assertThatIllegalStateException().isThrownBy(CandidateVolume.CUSTOM::count);
	}

	@Test
	void theDefaultIsNormalWithThreeBigSlots() {
		assertThat(DigestVolume.defaults()).isEqualTo(new DigestVolume(CandidateVolume.NORMAL, null, 3));
		assertThat(DigestVolume.defaults().maxCandidates()).isEqualTo(60);
	}

	@Test
	void theCustomCountComesOnlyWithCustom() {
		assertThat(DigestVolume.customCountMatches(CandidateVolume.CUSTOM, 45)).isTrue();
		assertThat(DigestVolume.customCountMatches(CandidateVolume.CUSTOM, null)).isFalse();
		assertThat(DigestVolume.customCountMatches(CandidateVolume.FEW, null)).isTrue();
		assertThat(DigestVolume.customCountMatches(CandidateVolume.FEW, 45)).isFalse();
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.NORMAL, 45, 3));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.CUSTOM, null, 3));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.CUSTOM, 0, 3));
		assertThatIllegalArgumentException().isThrownBy(() -> new DigestVolume(CandidateVolume.NORMAL, null, -1));
	}

	@Test
	void theConstructorReadsAStoredValueOutsideTodaysRange() {
		assertThat(new DigestVolume(CandidateVolume.CUSTOM, 200, 20).maxCandidates()).isEqualTo(200);
	}

	@Test
	void rangesIncludeBothEnds() {
		assertThat(DigestVolume.countInRange(9)).isFalse();
		assertThat(DigestVolume.countInRange(10)).isTrue();
		assertThat(DigestVolume.countInRange(150)).isTrue();
		assertThat(DigestVolume.countInRange(151)).isFalse();
		assertThat(DigestVolume.bigSlotsInRange(-1)).isFalse();
		assertThat(DigestVolume.bigSlotsInRange(0)).isTrue();
		assertThat(DigestVolume.bigSlotsInRange(10)).isTrue();
		assertThat(DigestVolume.bigSlotsInRange(11)).isFalse();
	}

}
