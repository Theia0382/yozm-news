package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.InvitationRevocationResult;
import net.yozmnews.user.InvitationState;
import net.yozmnews.user.InvitationView;
import net.yozmnews.user.IssuedInvitation;
import net.yozmnews.user.UnknownInvitationException;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.account.AccountRole;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.invitation.InvitationRepository;
import net.yozmnews.user.domain.secret.SecretDigest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 초대의 운영 — 토큰은 발급 결과에 한 번 나가고 다이제스트로만 저장되며, 메모는 앞뒤 공백을 지운 100 코드 포인트까지, 목록은 최근부터 쓴 계정의
 * 이메일과 함께, 취소는 유효한 초대만, 상태 확인은 읽을 수 없는 토큰을 가르지 않는다.
 * <p>
 * 지키는 규칙: user-R31
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class InvitationServiceTests {

	private static final Instant T0 = Instant.parse("2026-11-13T09:00:00Z");

	@Autowired
	InvitationRepository invitations;

	@Autowired
	AccountRepository accounts;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from user_invitations").update();
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void issuedTokenIsStoredOnlyAsItsDigest() {
		IssuedInvitation.Issued issued = issued(service(T0).issue("  김요즘  "));

		assertThat(issued.token()).matches("^[A-Za-z0-9_-]{22}$");
		assertThat(issued.expiresAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
		byte[] stored = this.jdbc.sql("select token_digest from user_invitations where id = :id")
			.param("id", issued.id())
			.query(byte[].class)
			.single();
		assertThat(SecretDigest.stored(stored).matches(SecretDigest.of(issued.token()))).isTrue();
		assertThat(this.jdbc.sql("select count(*) from user_invitations where memo = '김요즘'").query(Integer.class).single())
			.isOne();
	}

	@Test
	void memoIsTrimmedEmptyMeansNoneAndItsLengthIsCapped() {
		InvitationService service = service(T0);

		assertThat(service.issue("가".repeat(100))).isInstanceOf(IssuedInvitation.Issued.class);
		assertThat(service.issue("가".repeat(101))).isEqualTo(new IssuedInvitation.MemoTooLong(100));
		long blank = issued(service.issue("   ")).id();
		long none = issued(service.issue(null)).id();

		assertThat(service.recent()).filteredOn(view -> view.id() == blank || view.id() == none)
			.extracting(InvitationView::memo)
			.containsOnlyNulls();
		assertThat(this.jdbc.sql("select count(*) from user_invitations").query(Integer.class).single()).isEqualTo(3);
	}

	@Test
	void recentListsTheNewestFiftyWithTheirStateAtReadTime() {
		InvitationService issuing = service(T0);
		for (int i = 0; i < 51; i++) {
			issuing.issue("초대 " + i);
		}

		List<InvitationView> recent = service(T0.plus(Duration.ofDays(7))).recent();

		assertThat(recent).hasSize(50);
		assertThat(recent.getFirst().memo()).isEqualTo("초대 50");
		assertThat(recent.getLast().memo()).isEqualTo("초대 1");
		assertThat(recent).extracting(InvitationView::state).containsOnly(InvitationState.EXPIRED);
	}

	@Test
	void usedInvitationShowsTheEmailOfTheAccountThatSignedUpUntilThatAccountIsGone() {
		long id = issued(service(T0).issue("김요즘")).id();
		UserId user = this.accounts.save(Account.create(EmailAddress.parse("kim@example.com").orElseThrow(),
				new DisplayName("김요즘"), AccountRole.USER, T0)).id();
		Instant used = T0.plusSeconds(60);
		this.invitations.save(this.invitations.findById(id).orElseThrow().use(user, used));

		InvitationView view = service(T0.plusSeconds(120)).recent().getFirst();
		assertThat(view.state()).isEqualTo(InvitationState.USED);
		assertThat(view.usedAt()).isEqualTo(used);
		assertThat(view.usedByEmail()).isEqualTo("kim@example.com");

		this.jdbc.sql("delete from users where id = :id").param("id", user.value()).update();
		InvitationView afterDeletion = service(T0.plusSeconds(120)).recent().getFirst();
		assertThat(afterDeletion.state()).as("계정이 지워져도 쓴 시각이 남아 쓴 초대다").isEqualTo(InvitationState.USED);
		assertThat(afterDeletion.usedAt()).isEqualTo(used);
		assertThat(afterDeletion.usedByEmail()).isNull();
	}

	@Test
	void revokingWorksOnlyOnAValidInvitation() {
		long id = issued(service(T0).issue(null)).id();

		assertThat(service(T0).revoke(id)).isEqualTo(new InvitationRevocationResult.Revoked());
		assertThat(service(T0).revoke(id))
			.isEqualTo(new InvitationRevocationResult.NotRevocable(InvitationState.REVOKED));
		long expired = issued(service(T0).issue(null)).id();
		assertThat(service(T0.plus(Duration.ofDays(7))).revoke(expired))
			.isEqualTo(new InvitationRevocationResult.NotRevocable(InvitationState.EXPIRED));
		assertThatExceptionOfType(UnknownInvitationException.class).isThrownBy(() -> service(T0).revoke(id + 1000));
	}

	@Test
	void checkingTellsTheStateAndUnreadableOrUnknownTokensApart() {
		IssuedInvitation.Issued issued = issued(service(T0).issue(null));
		InvitationService service = service(T0);

		assertThat(service.check(issued.token(), T0)).contains(InvitationState.VALID);
		assertThat(service.check(issued.token(), issued.expiresAt())).contains(InvitationState.EXPIRED);
		assertThat(service.check("not a token", T0)).isEqualTo(Optional.empty());
		assertThat(service.check("AAAAAAAAAAAAAAAAAAAAAA", T0)).isEqualTo(Optional.empty());
	}

	private InvitationService service(Instant now) {
		return new InvitationService(this.invitations, this.accounts, () -> now);
	}

	private static IssuedInvitation.Issued issued(IssuedInvitation result) {
		assertThat(result).isInstanceOf(IssuedInvitation.Issued.class);
		return (IssuedInvitation.Issued) result;
	}

}
