package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.yozmnews.user.InvitationState;
import net.yozmnews.user.PasswordProblem;
import net.yozmnews.user.domain.account.DisplayName;
import net.yozmnews.user.domain.account.EmailAddress;
import net.yozmnews.user.domain.account.Onboarding;
import net.yozmnews.user.domain.credential.PasswordPolicy;
import net.yozmnews.user.domain.invitation.Invitation;
import net.yozmnews.user.domain.invitation.InvitationRepository;
import net.yozmnews.user.domain.secret.InvitationToken;
import net.yozmnews.user.domain.signup.SignUpPolicyRepository;
import org.springframework.stereotype.Service;

/**
 * 가입 유스케이스 — 공개 가입과 초대 가입. 한 번의 가입이 트랜잭션 하나다. docs/specs/user.md §4.
 * <p>
 * 칸의 형식(이메일 · 표시 이름 · 비밀번호) → 공개 가입은 가입 정책, 초대 가입은 초대를 잠그고 상태 → 계정을 만드는 정본으로 계정 · 자격 ·
 * 복구 코드 → (초대 가입이면) 초대를 쓴 것으로. 같은 이메일은 정본이 먼저 거르고, 먼저 본 뒤 끼어든 같은 이메일은 고유 제약이 트랜잭션을 깨므로
 * 트랜잭션 바깥에서 같은 결과로 옮긴다.
 */
@Service
public class SignUp {

	private final AccountProvisioning provisioning;

	private final SignUpPolicyRepository policy;

	private final InvitationRepository invitations;

	SignUp(AccountProvisioning provisioning, SignUpPolicyRepository policy, InvitationRepository invitations) {
		this.provisioning = Objects.requireNonNull(provisioning, "provisioning");
		this.policy = Objects.requireNonNull(policy, "policy");
		this.invitations = Objects.requireNonNull(invitations, "invitations");
	}

	/** 공개 가입 — 가입 정책이 열렸을 때만 */
	public SignUpOutcome openSignUp(SignUpForm form, Instant now) {
		return inTransaction(() -> {
			Optional<SignUpOutcome> malformed = malformed(form);
			if (malformed.isPresent()) {
				return malformed.get();
			}
			if (!this.policy.open().orElseThrow(() -> new IllegalStateException("가입 정책의 처음 값이 없다"))) {
				return new SignUpOutcome.Closed();
			}
			return signedUp(provision(Onboarding.OPEN_SIGN_UP, form, now));
		});
	}

	/** 초대 가입 — 유효한 초대일 때만. 같은 초대의 동시 가입은 초대 잠금에서 줄을 서고 하나만 쓴다 */
	public SignUpOutcome acceptInvitation(String rawToken, SignUpForm form, Instant now) {
		return inTransaction(() -> {
			Optional<SignUpOutcome> malformed = malformed(form);
			if (malformed.isPresent()) {
				return malformed.get();
			}
			Optional<Invitation> invitation = InvitationToken.parse(rawToken)
				.flatMap(token -> this.invitations.lockByTokenDigest(token.digest().value()));
			if (invitation.isEmpty()) {
				return new SignUpOutcome.InvitationUnusable(Optional.empty());
			}
			InvitationState state = invitation.get().state(now);
			if (state != InvitationState.VALID) {
				return new SignUpOutcome.InvitationUnusable(Optional.of(state));
			}
			ProvisionResult result = provision(Onboarding.INVITATION, form, now);
			if (result instanceof ProvisionResult.Created created) {
				this.invitations.save(invitation.get().use(created.user(), now));
			}
			return signedUp(result);
		});
	}

	private SignUpOutcome inTransaction(Supplier<SignUpOutcome> work) {
		return this.provisioning.inOwnTransaction(work, SignUpOutcome.EmailUnavailable::new);
	}

	private static Optional<SignUpOutcome> malformed(SignUpForm form) {
		if (EmailAddress.parse(form.email()).isEmpty()) {
			return Optional.of(new SignUpOutcome.EmailMalformed());
		}
		if (DisplayName.parse(form.displayName()).isEmpty()) {
			return Optional.of(new SignUpOutcome.DisplayNameMalformed());
		}
		Optional<PasswordProblem> problem = PasswordPolicy.check(PasswordPolicy.normalize(form.password()));
		return problem.map(SignUpOutcome.PasswordRejected::new);
	}

	private ProvisionResult provision(Onboarding onboarding, SignUpForm form, Instant now) {
		return this.provisioning.provision(onboarding, EmailAddress.parse(form.email()).orElseThrow(),
				DisplayName.parse(form.displayName()).orElseThrow(), PasswordPolicy.normalize(form.password()), now);
	}

	private static SignUpOutcome signedUp(ProvisionResult result) {
		return switch (result) {
			case ProvisionResult.Created created -> new SignUpOutcome.SignedUp(created.user(),
					created.recoveryCode().orElseThrow(() -> new IllegalStateException("가입한 계정의 복구 코드가 없다")));
			case ProvisionResult.EmailTaken taken -> new SignUpOutcome.EmailUnavailable();
		};
	}

}
