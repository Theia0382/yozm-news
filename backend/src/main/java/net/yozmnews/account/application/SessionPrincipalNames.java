package net.yozmnews.account.application;

import java.util.Map;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.session.IndexResolver;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 세션 저장소의 사용자 이름 색인 해석기 — 세션의 보안 문맥에서 사용자 이름(사용자 ID 의 문자열)을 읽는다. docs/specs/account.md §4 · account-R7
 * <p>
 * 세션 저장소는 세션 변경을 곧바로 저장해, 요청이 세션을 처음 읽을 때(마지막 접근 시각)도 색인을 다시 계산한다. 보안 문맥을 읽지 못하는 세션은
 * 예외 대신 빈 색인으로 저장한다 — 같은 요청에서 {@link SessionSecurityContexts} 가 그 세션을 지운다.
 */
@Component
final class SessionPrincipalNames implements IndexResolver<Session> {

	private final PrincipalNameIndexResolver<Session> delegate = new PrincipalNameIndexResolver<>();

	@Override
	public Map<String, String> resolveIndexesFor(Session session) {
		try {
			return this.delegate.resolveIndexesFor(session);
		}
		catch (ConversionFailedException ex) {
			return Map.of();
		}
	}

	@Override
	public String toString() {
		return "SessionPrincipalNames";
	}

}
