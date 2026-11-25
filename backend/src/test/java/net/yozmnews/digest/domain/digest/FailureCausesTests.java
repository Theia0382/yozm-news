package net.yozmnews.digest.domain.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.user.GenerationFailureCause;
import org.junit.jupiter.api.Test;

/**
 * 실패 코드의 갈래 — 코드마다 사용자가 할 일의 갈래 하나. 스프링 없이 돈다. 기대 표는 손으로 쓴 것이다.
 */
class FailureCausesTests {

	@Test
	void everyFailureCodeHasItsCause() {
		Map<DigestFailure, GenerationFailureCause> expected = new EnumMap<>(DigestFailure.class);
		expected.put(DigestFailure.KEY_MISSING, GenerationFailureCause.KEY);
		expected.put(DigestFailure.KEY_INVALID, GenerationFailureCause.KEY);
		expected.put(DigestFailure.NO_USABLE_MODEL, GenerationFailureCause.KEY);
		expected.put(DigestFailure.PERMISSION_DENIED, GenerationFailureCause.PROVIDER_ACCOUNT);
		expected.put(DigestFailure.QUOTA_EXHAUSTED, GenerationFailureCause.PROVIDER_ACCOUNT);
		expected.put(DigestFailure.PROVIDER_UNAVAILABLE, GenerationFailureCause.TEMPORARY);
		expected.put(DigestFailure.PROVIDER_UNEXPECTED, GenerationFailureCause.TEMPORARY);
		expected.put(DigestFailure.INTERRUPTED, GenerationFailureCause.TEMPORARY);
		expected.put(DigestFailure.UNREADABLE_OUTPUT, GenerationFailureCause.OUTPUT);
		expected.put(DigestFailure.NO_CITATION, GenerationFailureCause.OUTPUT);
		expected.put(DigestFailure.OUTPUT_TRUNCATED, GenerationFailureCause.OUTPUT);
		expected.put(DigestFailure.OUTPUT_REFUSED, GenerationFailureCause.OUTPUT);

		// 표가 코드 열둘을 모두 덮는다 — 코드가 늘면 이 시험이 먼저 알린다
		assertThat(expected).hasSize(DigestFailure.values().length).hasSize(12);
		for (Map.Entry<DigestFailure, GenerationFailureCause> entry : expected.entrySet()) {
			assertThat(FailureCauses.of(entry.getKey())).as(entry.getKey().name()).isEqualTo(entry.getValue());
		}
	}

}
