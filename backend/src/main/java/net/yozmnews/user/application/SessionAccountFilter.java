package net.yozmnews.user.application;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.yozmnews.user.domain.account.Account;
import net.yozmnews.user.domain.account.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션의 사용자 ID 로 계정을 읽어 요청의 인증을 정한다 — 계정이 없거나 쓰지 않는 계정이면 세션을 지우고 익명으로 둔다. docs/specs/user.md
 * §4 · user-R7
 * <p>
 * 요청마다 정한 인증(역할 권한)은 그 요청의 보안 문맥에만 두고 세션에 저장하지 않는다 — 보안 문맥 저장은 명시적으로만 한다. 세션 체인 안에서만
 * 만든다(빈이 아니다 — 빈이면 서블릿 필터로도 등록돼 모든 요청에 돈다).
 */
final class SessionAccountFilter extends OncePerRequestFilter {

	private final AccountRepository accounts;

	private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

	SessionAccountFilter(AccountRepository accounts) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication stored = this.contexts.getContext().getAuthentication();
		if (stored instanceof AccountAuthentication session) {
			Optional<Account> account = this.accounts.findById(session.userId().value()).filter(Account::active);
			if (account.isPresent()) {
				SecurityContext context = this.contexts.createEmptyContext();
				context.setAuthentication(AccountAuthentication.forRequest(session.userId(), account.get().role()));
				this.contexts.setContext(context);
			}
			else {
				HttpSession httpSession = request.getSession(false);
				if (httpSession != null) {
					httpSession.invalidate();
				}
				this.contexts.clearContext();
			}
		}
		chain.doFilter(request, response);
	}

}
