package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 판정 결과와 판정 원본 ID 의 짝 — 원본은 확률이 있을 때만 있다.
 * <p>
 * 지키는 규칙: evaluation-R38
 */
class MembershipJudgementTests {

	@Test
	void aJudgementWithProbabilitiesCarriesItsRecord() {
		MembershipJudgement.Judged judged = new MembershipJudgement.Judged(new EventJudgementId(7), Map.of(12L, 0.83));

		assertThat(judged.judgement()).isEqualTo(new EventJudgementId(7));
		assertThat(judged.probabilities()).containsExactlyEntriesOf(Map.of(12L, 0.83));
	}

	@Test
	void aJudgementThatAskedNothingHasNoRecord() {
		assertThat(new MembershipJudgement.Judged(null, Map.of()).judgement()).isNull();
	}

	@Test
	void refusesProbabilitiesWithoutARecordAndARecordWithoutProbabilities() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipJudgement.Judged(null, Map.of(12L, 0.5)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipJudgement.Judged(new EventJudgementId(7), Map.of()));
	}

	@Test
	void aRecordIdStartsAtOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EventJudgementId(0));
		assertThat(new EventJudgementId(1).value()).isEqualTo(1);
	}

}
