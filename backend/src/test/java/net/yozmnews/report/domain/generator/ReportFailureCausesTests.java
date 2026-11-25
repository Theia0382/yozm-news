package net.yozmnews.report.domain.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import net.yozmnews.report.ReportFailure;
import net.yozmnews.user.GenerationFailureCause;
import org.junit.jupiter.api.Test;

/**
 * 리포트 실패 코드의 갈래 — 코드마다 사용자가 할 일의 갈래 하나. 기대값은 손으로 쓴 표다.
 */
class ReportFailureCausesTests {

	@Test
	void everyFailureCodeHasItsCause() {
		Map<ReportFailure, GenerationFailureCause> expected = new EnumMap<>(ReportFailure.class);
		expected.put(ReportFailure.KEY_MISSING, GenerationFailureCause.KEY);
		expected.put(ReportFailure.KEY_INVALID, GenerationFailureCause.KEY);
		expected.put(ReportFailure.NO_USABLE_MODEL, GenerationFailureCause.KEY);
		expected.put(ReportFailure.PERMISSION_DENIED, GenerationFailureCause.PROVIDER_ACCOUNT);
		expected.put(ReportFailure.QUOTA_EXHAUSTED, GenerationFailureCause.PROVIDER_ACCOUNT);
		expected.put(ReportFailure.PROVIDER_UNAVAILABLE, GenerationFailureCause.TEMPORARY);
		expected.put(ReportFailure.PROVIDER_UNEXPECTED, GenerationFailureCause.TEMPORARY);
		expected.put(ReportFailure.INTERRUPTED, GenerationFailureCause.TEMPORARY);
		expected.put(ReportFailure.UNREADABLE_OUTPUT, GenerationFailureCause.OUTPUT);
		expected.put(ReportFailure.OUTPUT_TRUNCATED, GenerationFailureCause.OUTPUT);
		expected.put(ReportFailure.OUTPUT_REFUSED, GenerationFailureCause.OUTPUT);

		// 표가 코드 열하나를 모두 덮는다 — 코드가 늘면 이 시험이 먼저 알린다
		assertThat(expected).hasSize(ReportFailure.values().length).hasSize(11);
		for (Map.Entry<ReportFailure, GenerationFailureCause> entry : expected.entrySet()) {
			assertThat(ReportFailureCauses.of(entry.getKey())).as(entry.getKey().name()).isEqualTo(entry.getValue());
		}
	}

}
