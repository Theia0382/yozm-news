package net.yozmnews.account.application;

import java.util.Objects;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.RequestPath;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * 세션 체인에서 요청의 처리기를 찾는다 — 인가와 요청 방어 필터가 함께 쓰는 정본. docs/specs/account.md §4 · account-R5 · account-R32
 * <p>
 * 체인은 {@code DispatcherServlet} 앞이라 처리기 매핑에 묻기 전에 요청 경로를 파싱해 두고, 물은 뒤 원래 상태로 돌린다.
 */
final class HandlerLookup {

	private final RequestMappingHandlerMapping mapping;

	HandlerLookup(RequestMappingHandlerMapping mapping) {
		this.mapping = Objects.requireNonNull(mapping, "mapping");
	}

	/**
	 * 요청을 받을 처리기.
	 * @return 처리기가 없으면(없는 경로 · 다른 메서드 · 받지 않는 미디어 타입) null
	 * @throws IllegalStateException 처리기 매핑이 그 밖의 이유로 실패했을 때
	 */
	@Nullable HandlerMethod find(HttpServletRequest request) {
		boolean parsed = ServletRequestPathUtils.hasParsedRequestPath(request);
		@Nullable RequestPath previous = parsed ? ServletRequestPathUtils.getParsedRequestPath(request) : null;
		if (!parsed) {
			ServletRequestPathUtils.parseAndCache(request);
		}
		try {
			@Nullable HandlerExecutionChain chain = this.mapping.getHandler(request);
			return (chain != null && chain.getHandler() instanceof HandlerMethod method) ? method : null;
		}
		catch (ServletException ex) {
			// 메서드 · 미디어 타입 · 질의 조건이 맞지 않으면 매핑이 ServletException 을 낸다 — 처리기가 없는 요청이다
			return null;
		}
		catch (RuntimeException ex) {
			// 그 밖의 실패는 열지 않는다 — 처리기를 판정하지 못한 요청이다
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("요청의 처리기를 찾지 못했다", ex);
		}
		finally {
			ServletRequestPathUtils.setParsedRequestPath(previous, request);
		}
	}

}
