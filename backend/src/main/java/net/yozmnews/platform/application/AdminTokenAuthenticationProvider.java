package net.yozmnews.platform.application;

import java.util.Objects;

import net.yozmnews.platform.domain.AdminCredential;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/**
 * 제시된 운영자 토큰을 운영자 자격과 대조한다 — 대조는 여기 한 곳에서만 한다.
 */
final class AdminTokenAuthenticationProvider implements AuthenticationProvider {

	private final AdminCredential credential;

	AdminTokenAuthenticationProvider(AdminCredential credential) {
		this.credential = Objects.requireNonNull(credential, "credential");
	}

	/**
	 * 맞으면 운영자 인증을 돌려준다.
	 * @throws BadCredentialsException 토큰이 맞지 않거나 운영자 기능이 닫혔을 때 — 메시지에 토큰을 담지 않는다
	 */
	@Override
	public Authentication authenticate(Authentication authentication) {
		Object token = authentication.getCredentials();
		if (token instanceof String presented && this.credential.matches(presented)) {
			return AdminTokenAuthentication.operator();
		}
		throw new BadCredentialsException("운영자 토큰이 맞지 않는다");
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return AdminTokenAuthentication.class.isAssignableFrom(authentication);
	}

}
