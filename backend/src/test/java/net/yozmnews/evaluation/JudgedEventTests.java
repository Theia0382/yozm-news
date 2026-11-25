package net.yozmnews.evaluation;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 사건 소속 판정 원본에 남은 물은 후보 하나 — 사건 ID · 서술 기사 · 확률을 확인한다.
 * <p>
 * 지키는 규칙: evaluation-R38
 */
class JudgedEventTests {

	@Test
	void refusesANonPositiveEventIdNoDescriptionsOrAProbabilityOutsideZeroToOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> new JudgedEvent(0, List.of(new ArticleId(3)), 0.83));
		assertThatIllegalArgumentException().isThrownBy(() -> new JudgedEvent(12, List.of(), 0.83));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new JudgedEvent(12, List.of(new ArticleId(3)), 1.01));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new JudgedEvent(12, List.of(new ArticleId(3)), Double.NaN));
	}

}
