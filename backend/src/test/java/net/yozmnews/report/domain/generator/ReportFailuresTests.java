package net.yozmnews.report.domain.generator;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.report.ReportFailure;
import org.junit.jupiter.api.Test;

/**
 * 관문 실패 종류의 옮기기 — 종류마다 코드 하나, 다이제스트와 같은 이름.
 * <p>
 * 지키는 규칙: report-R24
 */
class ReportFailuresTests {

	@Test
	void eachGatewayFailureKindHasItsOwnCode() {
		// 아래 다섯이 관문의 실패 종류 전부다 — 종류가 늘면 이 시험부터 고친다
		assertThat(LlmFailureKind.values()).hasSize(5);
		assertThat(ReportFailures.of(LlmFailureKind.INVALID_KEY)).isEqualTo(ReportFailure.KEY_INVALID);
		assertThat(ReportFailures.of(LlmFailureKind.PERMISSION_DENIED)).isEqualTo(ReportFailure.PERMISSION_DENIED);
		assertThat(ReportFailures.of(LlmFailureKind.QUOTA_EXHAUSTED)).isEqualTo(ReportFailure.QUOTA_EXHAUSTED);
		assertThat(ReportFailures.of(LlmFailureKind.TRANSIENT)).isEqualTo(ReportFailure.PROVIDER_UNAVAILABLE);
		assertThat(ReportFailures.of(LlmFailureKind.UNEXPECTED)).isEqualTo(ReportFailure.PROVIDER_UNEXPECTED);
	}

}
