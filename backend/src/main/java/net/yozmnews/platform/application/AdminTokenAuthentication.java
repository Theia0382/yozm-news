package net.yozmnews.platform.application;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * 운영자 토큰 인증 — 제시된 토큰(인증 전)과 운영자 권한(인증 뒤). 인증 뒤에는 토큰을 들고 있지 않다.
 */
final class AdminTokenAuthentication extends AbstractAuthenticationToken {

	private static final String PRINCIPAL = "operator";

	private final @Nullable String token;

	private AdminTokenAuthentication(@Nullable String token, boolean authenticated) {
		super(authenticated ? AuthorityUtils.createAuthorityList("ROLE_" + SecurityConfiguration.ADMIN_ROLE) : List.of());
		this.token = token;
		super.setAuthenticated(authenticated);
	}

	/** 요청이 제시한 토큰 — 아직 인증하지 않았다 */
	static AdminTokenAuthentication presented(String token) {
		return new AdminTokenAuthentication(token, false);
	}

	/** 운영자로 인증했다 */
	static AdminTokenAuthentication operator() {
		return new AdminTokenAuthentication(null, true);
	}

	@Override
	public @Nullable Object getCredentials() {
		return this.token;
	}

	@Override
	public Object getPrincipal() {
		return PRINCIPAL;
	}

	@Override
	public void setAuthenticated(boolean authenticated) {
		throw new IllegalArgumentException("인증 여부는 만들 때 정한다");
	}

	@Override
	public String toString() {
		return "AdminTokenAuthentication[" + (isAuthenticated() ? "operator" : "presented") + "]";
	}

}
