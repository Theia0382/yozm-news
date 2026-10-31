package net.yozmnews.platform.application;

import java.net.URI;

/**
 * 운영자 접근 통제가 내는 문제 유형과 제목 — 정본. 그 밖의 문제는 {@code about:blank} 다(docs/specs/platform.md §5).
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

	private ProblemTypes() {
	}

}
