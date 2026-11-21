package net.yozmnews.digest.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.user.SummaryLength;
import org.junit.jupiter.api.Test;

/**
 * 길이 기준과 출력 서술의 상한 — 기준의 위쪽이 상한을 넘으면 기준대로 쓴 개요가 출력 전체를 읽지 못하게 하고 요약은 그 항목 칸을 버리게 한다.
 * 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R5 · digest-R26
 */
class PromptRulesTests {

	@Test
	void everyLengthGuideFitsTheOutputLimits() {
		for (SummaryLength length : SummaryLength.values()) {
			assertThat(PromptRules.overview(length).max()).as("개요 %s", length)
				.isLessThanOrEqualTo(DigestOutputSchema.MAX_OVERVIEW);
			assertThat(PromptRules.summary(length).max()).as("주요 뉴스 요약 %s", length)
				.isLessThanOrEqualTo(DigestOutputSchema.MAX_SUMMARY);
		}
	}

}
