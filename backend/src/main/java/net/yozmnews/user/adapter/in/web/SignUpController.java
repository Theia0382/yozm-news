package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.SignUpPolicyAdministration;
import net.yozmnews.user.application.InvitationService;
import net.yozmnews.user.application.SessionSignIn;
import net.yozmnews.user.application.SignUp;
import net.yozmnews.user.application.SignUpForm;
import net.yozmnews.user.application.SignUpOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 — 가입 정책 조회, 공개 가입, 초대 상태 확인, 초대 가입. 가입하면 들고 온 세션을 버리고 새 세션으로 로그인한다. docs/specs/user.md §3
 */
@WebRole
@RestController
class SignUpController {

	private final SignUpPolicyAdministration policy;

	private final SignUp signUp;

	private final InvitationService invitations;

	private final SessionSignIn sessions;

	private final InstantSource clock;

	@Autowired
	SignUpController(SignUpPolicyAdministration policy, SignUp signUp, InvitationService invitations,
			SessionSignIn sessions) {
		this(policy, signUp, invitations, sessions, InstantSource.system());
	}

	SignUpController(SignUpPolicyAdministration policy, SignUp signUp, InvitationService invitations,
			SessionSignIn sessions, InstantSource clock) {
		this.policy = Objects.requireNonNull(policy, "policy");
		this.signUp = Objects.requireNonNull(signUp, "signUp");
		this.invitations = Objects.requireNonNull(invitations, "invitations");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@PublicApi(reason = "로그인 · 가입 화면이 가입 링크를 보일지 정한다")
	@GetMapping("/api/sign-up-policy")
	SignUpPolicyResponse signUpPolicy() {
		return new SignUpPolicyResponse(this.policy.open());
	}

	@PublicApi(reason = "공개 가입 — 계정이 없는 사람이 쓴다")
	@PostMapping("/api/sign-ups")
	SignUpResponse signUp(@Valid @RequestBody SignUpRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		SignUpOutcome outcome = this.signUp.openSignUp(new SignUpForm(body.email(), body.displayName(), body.password()),
				this.clock.instant());
		signInIfSignedUp(outcome, body.keep(), request, response);
		return SignUpResponse.of(outcome);
	}

	@PublicApi(reason = "초대 링크를 연 사람이 가입 칸을 볼지 정한다")
	@PostMapping("/api/invitation-checks")
	InvitationCheckResponse checkInvitation(@Valid @RequestBody InvitationCheckRequest body) {
		return new InvitationCheckResponse(
				InvitationStatus.of(this.invitations.check(body.token(), this.clock.instant())));
	}

	@PublicApi(reason = "초대 가입 — 계정이 없는 사람이 쓴다")
	@PostMapping("/api/invitation-acceptances")
	SignUpResponse acceptInvitation(@Valid @RequestBody InvitationAcceptanceRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Instant now = this.clock.instant();
		SignUpOutcome outcome = this.signUp.acceptInvitation(body.token(),
				new SignUpForm(body.email(), body.displayName(), body.password()), now);
		signInIfSignedUp(outcome, body.keep(), request, response);
		return SignUpResponse.of(outcome);
	}

	private void signInIfSignedUp(SignUpOutcome outcome, boolean keep, HttpServletRequest request,
			HttpServletResponse response) {
		if (outcome instanceof SignUpOutcome.SignedUp signedUp) {
			this.sessions.signInAfresh(signedUp.user(), keep, request, response);
		}
	}

}
