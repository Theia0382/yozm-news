package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.UserId;
import net.yozmnews.user.application.AccountRecovery;
import net.yozmnews.user.application.PasswordChange;
import net.yozmnews.user.application.PasswordChangeOutcome;
import net.yozmnews.user.application.RecoveryCodeReissue;
import net.yozmnews.user.application.RecoveryOutcome;
import net.yozmnews.user.application.SessionSignIn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정의 비밀번호 · 복구 — 비밀번호 변경, 복구 코드 재발급, 복구 코드로 비밀번호 재설정. docs/specs/user.md §3
 */
@WebRole
@RestController
class AccountController {

	private final PasswordChange passwordChange;

	private final RecoveryCodeReissue reissue;

	private final AccountRecovery recovery;

	private final SessionSignIn sessions;

	private final InstantSource clock;

	@Autowired
	AccountController(PasswordChange passwordChange, RecoveryCodeReissue reissue, AccountRecovery recovery,
			SessionSignIn sessions) {
		this(passwordChange, reissue, recovery, sessions, InstantSource.system());
	}

	AccountController(PasswordChange passwordChange, RecoveryCodeReissue reissue, AccountRecovery recovery,
			SessionSignIn sessions, InstantSource clock) {
		this.passwordChange = Objects.requireNonNull(passwordChange, "passwordChange");
		this.reissue = Objects.requireNonNull(reissue, "reissue");
		this.recovery = Objects.requireNonNull(recovery, "recovery");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 비밀번호 변경 — 바꾸면 이 세션의 ID 와 CSRF 토큰을 바꾸고 다른 세션은 끝난다 */
	@AllowedBeforePasswordChange(reason = "초기 비밀번호를 바꾸는 길이다")
	@PutMapping("/api/account/password")
	PasswordChangeResponse changePassword(UserId user, @Valid @RequestBody PasswordChangeRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		Instant now = this.clock.instant();
		String sessionId = request.getSession().getId();
		PasswordChangeOutcome outcome = this.passwordChange.change(user, body.currentPassword(), body.newPassword(),
				sessionId, now);
		if (outcome instanceof PasswordChangeOutcome.Changed) {
			this.sessions.renew(request, response);
		}
		return PasswordChangeResponse.of(outcome, now);
	}

	/** 복구 코드 재발급 — 현재 비밀번호를 확인한다 */
	@PostMapping("/api/account/recovery-code")
	RecoveryCodeResponse reissueRecoveryCode(UserId user, @Valid @RequestBody RecoveryCodeRequest body) {
		Instant now = this.clock.instant();
		return RecoveryCodeResponse.of(this.reissue.reissue(user, body.currentPassword(), now), now);
	}

	/**
	 * 복구 코드로 비밀번호 재설정 — 재설정하면 그 계정의 세션이 모두 끝나고 새 세션으로 로그인한다. 이 요청이 들고 온 세션은 버리고 새 세션을
	 * 만든다 — 그 계정의 세션이었다면 재설정이 이미 저장소에서 지웠다
	 */
	@PublicApi(reason = "비밀번호를 잊어 로그인할 수 없는 사람이 쓴다")
	@PostMapping("/api/account-recoveries")
	AccountRecoveryResponse recover(@Valid @RequestBody AccountRecoveryRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Instant now = this.clock.instant();
		RecoveryOutcome outcome = this.recovery.reset(body.email(), body.recoveryCode(), body.newPassword(), now);
		if (outcome instanceof RecoveryOutcome.Reset reset) {
			this.sessions.signInAfresh(reset.user(), body.keep(), request, response);
		}
		return AccountRecoveryResponse.of(outcome, now);
	}

}
