package net.yozmnews.storyline.domain.storyline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import net.yozmnews.storyline.domain.linking.Score;
import org.junit.jupiter.api.Test;

/**
 * 소속의 판정 근거 — 판정한 소속은 점수 · 기준이 있고, 옮긴 소속은 없다.
 * <p>
 * 지키는 규칙: storyline-R14
 */
class LinkJudgementTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final Score SCORE = new Score(0.7, 0.8, 0.5);

	@Test
	void judgedAndMergedFillTheirFields() {
		assertThat(LinkJudgement.judged(LinkedBy.PAIR, SCORE, 3, NOW)).isEqualTo(new LinkJudgement(LinkedBy.PAIR, SCORE, 3L, NOW));
		assertThat(LinkJudgement.merged(NOW)).isEqualTo(new LinkJudgement(LinkedBy.MERGE, null, null, NOW));
	}

	@Test
	void mismatchedFieldsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.RULE, null, 3L, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.PAIR, SCORE, null, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.MERGE, SCORE, null, NOW));
		assertThatIllegalArgumentException().isThrownBy(() -> new LinkJudgement(LinkedBy.MERGE, null, 3L, NOW));
	}

}
