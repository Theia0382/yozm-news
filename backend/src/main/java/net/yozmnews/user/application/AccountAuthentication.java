package net.yozmnews.user.application;

import java.io.Serial;
import java.util.List;

import net.yozmnews.user.UserId;
import net.yozmnews.user.domain.account.AccountRole;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * 세션 사용자 인증 — 세션에 직렬화되는 유일한 앱 타입. docs/specs/user.md §4 · user-R7
 * <p>
 * 세션에는 권한 없는 형태({@link #stored})로 사용자 ID 만 담는다. 요청마다 계정을 읽어 권한을 단 형태({@link #forRequest})를 그 요청의
 * 보안 문맥에만 둔다 — 권한은 역할 하나이고, 비밀번호를 바꿔야 하는 계정은 역할 대신 {@value #PASSWORD_CHANGE_REQUIRED} 하나다. 사용자
 * 이름(세션 저장소의 {@code PRINCIPAL_NAME})은 사용자 ID 의 문자열이다.
 */
public final class AccountAuthentication extends AbstractAuthenticationToken {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 비밀번호를 바꿔야 하는 계정의 유일한 권한 — 역할 권한을 두지 않는다(user-R7) */
	static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

	private final long userId;

	private AccountAuthentication(long userId, List<String> authorities) {
		super(AuthorityUtils.createAuthorityList(authorities.toArray(String[]::new)));
		this.userId = userId;
		super.setAuthenticated(true);
	}

	/** 세션에 담는 형태 — 사용자 ID 만 */
	static AccountAuthentication stored(UserId userId) {
		return new AccountAuthentication(userId.value(), List.of());
	}

	/**
	 * 요청에서 쓰는 형태 — 계정의 역할 권한을 단다. 비밀번호를 바꿔야 하는 계정은 역할 권한 대신 {@value #PASSWORD_CHANGE_REQUIRED} 하나를
	 * 단다 — 그동안 역할 판정(운영자 경로 등)을 통과하지 않는다.
	 */
	static AccountAuthentication forRequest(UserId userId, AccountRole role, boolean mustChangePassword) {
		return new AccountAuthentication(userId.value(),
				List.of(mustChangePassword ? PASSWORD_CHANGE_REQUIRED : "ROLE_" + role.name()));
	}

	/** 세션 사용자 */
	public UserId userId() {
		return new UserId(this.userId);
	}

	/** 이 요청의 세션 사용자가 비밀번호를 바꿔야 하는가 — 세션에 담는 형태에서는 언제나 거짓이다 */
	public boolean mustChangePassword() {
		return getAuthorities().stream().anyMatch(authority -> PASSWORD_CHANGE_REQUIRED.equals(authority.getAuthority()));
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public Object getPrincipal() {
		return this.userId;
	}

	@Override
	public String getName() {
		return Long.toString(this.userId);
	}

	@Override
	public void setAuthenticated(boolean authenticated) {
		throw new IllegalArgumentException("인증 여부는 만들 때 정한다");
	}

}
