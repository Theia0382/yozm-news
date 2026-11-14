package net.yozmnews.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 사용자 식별자의 범위.
 * <p>
 * 지키는 계약: docs/specs/platform.md §3 {@code UserId} 의 값 제약(1 이상) · platform-R24
 */
class UserIdTests {

	@Test
	void acceptsOneAndAbove() {
		assertThat(new UserId(1).value()).isEqualTo(1L);
		assertThat(new UserId(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	void rejectsZeroAndNegativeValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> new UserId(0))
			.withMessage("사용자 식별자는 1 이상이어야 한다: 0");
		assertThatIllegalArgumentException().isThrownBy(() -> new UserId(-1))
			.withMessage("사용자 식별자는 1 이상이어야 한다: -1");
	}

}
