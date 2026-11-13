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
 * 세션에는 권한 없는 형태({@link #stored})로 사용자 ID 만 담는다. 요청마다 계정을 읽어 역할 권한을 단 형태({@link #forRequest})를 그 요청의
 * 보안 문맥에만 둔다. 사용자 이름(세션 저장소의 {@code PRINCIPAL_NAME})은 사용자 ID 의 문자열이다.
 */
public final class AccountAuthentication extends AbstractAuthenticationToken {

	@Serial
	private static final long serialVersionUID = 1L;

	private final long userId;

	private AccountAuthentication(long userId, List<String> roles) {
		super(AuthorityUtils.createAuthorityList(roles.stream().map(role -> "ROLE_" + role).toArray(String[]::new)));
		this.userId = userId;
		super.setAuthenticated(true);
	}

	/** 세션에 담는 형태 — 사용자 ID 만 */
	static AccountAuthentication stored(UserId userId) {
		return new AccountAuthentication(userId.value(), List.of());
	}

	/** 요청에서 쓰는 형태 — 계정의 역할 권한을 단다 */
	static AccountAuthentication forRequest(UserId userId, AccountRole role) {
		return new AccountAuthentication(userId.value(), List.of(role.name()));
	}

	/** 세션 사용자 */
	public UserId userId() {
		return new UserId(this.userId);
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
