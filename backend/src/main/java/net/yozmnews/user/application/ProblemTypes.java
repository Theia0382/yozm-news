package net.yozmnews.user.application;

import java.net.URI;

/**
 * 보안 거부(운영자 접근 통제 · 세션 체인)가 내는 문제 유형과 제목 — 정본. 그 밖의 문제는 {@code about:blank} 다(docs/specs/user.md §4 ·
 * user-R3).
 */
final class ProblemTypes {

	/** 운영자 토큰을 설정하지 않아 운영자 기능이 닫혔다 — 403 */
	static final URI ADMIN_CLOSED = URI.create("urn:yozmnews:problem:admin-closed");

	static final String ADMIN_CLOSED_TITLE = "운영자 기능이 닫혀 있다";

	/** 운영자 토큰이 없거나 맞지 않는다 — 401 */
	static final URI ADMIN_UNAUTHORIZED = URI.create("urn:yozmnews:problem:admin-unauthorized");

	static final String ADMIN_UNAUTHORIZED_TITLE = "운영자 인증이 필요하다";

	/** 인증했지만 운영자 권한이 없다 — 403 */
	static final URI ADMIN_FORBIDDEN = URI.create("urn:yozmnews:problem:admin-forbidden");

	static final String ADMIN_FORBIDDEN_TITLE = "운영자 권한이 없다";

	/** 세션이 없거나 만료됐다 — 401 */
	static final URI UNAUTHENTICATED = URI.create("urn:yozmnews:problem:unauthenticated");

	static final String UNAUTHENTICATED_TITLE = "로그인이 필요하다";

	/** CSRF 토큰이 없거나 맞지 않는다 — 403 */
	static final URI CSRF_REJECTED = URI.create("urn:yozmnews:problem:csrf-rejected");

	static final String CSRF_REJECTED_TITLE = "요청 보호 토큰이 맞지 않는다";

	/** 비밀번호를 바꿔야 하는 세션 사용자의 요청을 그 전에 여는 처리기 밖에서 거부했다 — 403 */
	static final URI PASSWORD_CHANGE_REQUIRED = URI.create("urn:yozmnews:problem:password-change-required");

	static final String PASSWORD_CHANGE_REQUIRED_TITLE = "비밀번호를 바꿔야 한다";

	/** LLM 키가 없는 세션 사용자의 요청을 키 없이도 여는 처리기 밖에서 거부했다 — 403 */
	static final URI LLM_KEY_REQUIRED = URI.create("urn:yozmnews:problem:llm-key-required");

	static final String LLM_KEY_REQUIRED_TITLE = "LLM 키를 등록해야 한다";

	private ProblemTypes() {
	}

}
