package net.yozmnews.user.domain.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 초대 메모 — 앞뒤 공백을 지우고 비면 없음, 100 코드 포인트까지.
 * <p>
 * 지키는 규칙: user-R31
 */
class InvitationPolicyTests {

	@Test
	void memoIsStrippedAndBlankMeansNone() {
		assertThat(InvitationPolicy.memo("  김요즘  ")).contains("김요즘");
		assertThat(InvitationPolicy.memo(" \t ")).isEmpty();
		assertThat(InvitationPolicy.memo(null)).isEmpty();
	}

	@Test
	void memoFitsUpToOneHundredCodePoints() {
		assertThat(InvitationPolicy.memoFits("가".repeat(100))).isTrue();
		assertThat(InvitationPolicy.memoFits("가".repeat(101))).isFalse();
		// U+1F600 은 char 둘, 코드 포인트 하나다
		assertThat(InvitationPolicy.memoFits("😀".repeat(100))).isTrue();
	}

}
