package net.yozmnews.user.domain.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.user.InvitationState;
import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.secret.SecretDigest;
import org.junit.jupiter.api.Test;

/**
 * 초대 — 발급에서 7일 뒤 만료(만료 시각 포함), 쓴 것 · 취소한 것 · 만료한 것 · 유효한 것 순의 상태, 유효할 때만 쓰고 취소한다.
 * <p>
 * 지키는 규칙: user-R31
 */
class InvitationTests {

	private static final Instant ISSUED = Instant.parse("2026-11-13T09:00:00Z");

	private static final Instant EXPIRY = ISSUED.plus(Duration.ofDays(7));

	private final Invitation invitation = Invitation.issue(SecretDigest.of("token"), "김요즘", ISSUED);

	@Test
	void expiresSevenDaysAfterIssuingAndTheExpiryInstantIsAlreadyExpired() {
		assertThat(this.invitation.expiresAt()).isEqualTo(Instant.parse("2026-11-20T09:00:00Z"));
		assertThat(this.invitation.state(EXPIRY.minusNanos(1))).isEqualTo(InvitationState.VALID);
		assertThat(this.invitation.state(EXPIRY)).isEqualTo(InvitationState.EXPIRED);
	}

	@Test
	void usedStaysUsedAfterTheExpiry() {
		Invitation used = this.invitation.use(new UserId(7), ISSUED.plusSeconds(60));

		assertThat(used.state(EXPIRY.plusSeconds(1))).isEqualTo(InvitationState.USED);
		assertThat(used.usedBy()).contains(new UserId(7));
		assertThat(used.usedAt()).contains(ISSUED.plusSeconds(60));
	}

	@Test
	void revokedStaysRevokedAfterTheExpiry() {
		Invitation revoked = this.invitation.revoke(ISSUED.plusSeconds(60));

		assertThat(revoked.state(EXPIRY.plusSeconds(1))).isEqualTo(InvitationState.REVOKED);
		assertThat(revoked.revokedAt()).contains(ISSUED.plusSeconds(60));
	}

	@Test
	void onlyAValidInvitationIsUsedOrRevoked() {
		Invitation used = this.invitation.use(new UserId(7), ISSUED);

		assertThatIllegalStateException().isThrownBy(() -> used.use(new UserId(8), ISSUED));
		assertThatIllegalStateException().isThrownBy(() -> used.revoke(ISSUED));
		assertThatIllegalStateException().isThrownBy(() -> this.invitation.use(new UserId(8), EXPIRY));
		assertThatIllegalStateException().isThrownBy(() -> this.invitation.revoke(ISSUED).use(new UserId(8), ISSUED));
	}

}
