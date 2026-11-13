package net.yozmnews.user.adapter.in.web;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.WebRole;
import net.yozmnews.user.application.AccountAuthentication;
import net.yozmnews.user.application.AccountQueries;
import net.yozmnews.user.application.SessionSignIn;
import net.yozmnews.user.application.SignIn;
import net.yozmnews.user.application.SignInOutcome;
import net.yozmnews.user.domain.account.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 · 로그아웃 · 로그인 상태 조회 — docs/specs/user.md §3 · user-R11 · user-R17 · user-R20
 */
@WebRole
@RestController
class SessionController {

	private final SignIn signIn;

	private final SessionSignIn sessions;

	private final AccountQueries accounts;

	private final InstantSource clock;

	@Autowired
	SessionController(SignIn signIn, SessionSignIn sessions, AccountQueries accounts) {
		this(signIn, sessions, accounts, InstantSource.system());
	}

	SessionController(SignIn signIn, SessionSignIn sessions, AccountQueries accounts, InstantSource clock) {
		this.signIn = Objects.requireNonNull(signIn, "signIn");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@PublicApi(reason = "로그인")
	@PostMapping("/api/session")
	SignInResponse signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Instant now = this.clock.instant();
		return switch (this.signIn.attempt(body.email(), body.password(), now)) {
			case SignInOutcome.SignedIn signedIn -> {
				this.sessions.signIn(signedIn.userId(), body.keep(), request, response);
				yield SignInResponse.signedIn();
			}
			case SignInOutcome.Rejected rejected -> SignInResponse.rejected();
			case SignInOutcome.Wait wait -> SignInResponse.waiting(wait.retryAt(), now);
		};
	}

	@PublicApi(reason = "로그아웃 — 세션이 없어도 같은 결과")
	@DeleteMapping("/api/session")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void signOut(HttpServletRequest request, HttpServletResponse response) {
		this.sessions.signOut(request, response);
	}

	@PublicApi(reason = "로그인 상태 조회")
	@GetMapping("/api/me")
	MeResponse me() {
		Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
		Optional<Account> account = (authentication instanceof AccountAuthentication session)
				? this.accounts.active(session.userId()) : Optional.empty();
		return account.map(MeResponse::signedIn).orElseGet(MeResponse::anonymous);
	}

}
