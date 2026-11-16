package net.yozmnews.storyline.domain.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.domain.linking.Asked;
import net.yozmnews.storyline.domain.linking.Score;
import org.junit.jupiter.api.Test;

/**
 * 소속의 판정 근거 — 판정한 소속은 점수 · 기준이 있고 띠 안에서 정했으면 확률 · 판정 원본이 있다. 옮긴 소속은 모두 없다.
 * <p>
 * 지키는 규칙: storyline-R14 · storyline-R35
 */
class LinkJudgementTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final Score SCORE = new Score(0.7, 0.8, 0.5);

	@Test
	void judgedAndMergedFillTheirFields() {
		assertThat(LinkJudgement.judged(LinkedBy.PAIR, SCORE, 3, null, NOW))
			.isEqualTo(new LinkJudgement(LinkedBy.PAIR, SCORE, 3L, null, NOW));
		assertThat(LinkJudgement.judged(LinkedBy.RULE, SCORE, 3, new Asked(0.71, 9L), NOW).asked())
			.isEqualTo(new Asked(0.71, 9L));
		assertThat(LinkJudgement.merged(NOW)).isEqualTo(new LinkJudgement(LinkedBy.MERGE, null, null, null, NOW));
	}

	@Test
	void mismatchedFieldsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.RULE, null, 3L, null, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.PAIR, SCORE, null, null, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.MERGE, SCORE, null, null, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.MERGE, null, 3L, null, NOW));
		// 옮긴 소속은 판정 결과가 없고, 판정 결과로 정한 소속은 확률이 있다
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LinkJudgement(LinkedBy.MERGE, null, null, new Asked(0.71, 9L), NOW));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LinkJudgement(LinkedBy.RULE, SCORE, 3L, Asked.UNANSWERED, NOW));
	}

}
