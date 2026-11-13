package net.yozmnews.platform;

/**
 * 화면용 API 경로 규약 — 경로 패턴의 코드 쪽 정본. docs/specs/platform.md §3 · platform-R21
 */
public final class ApiPaths {

	/** 화면용 API 경로 — 명세가 담고 세션 체인이 맡는다 */
	public static final String API = "/api/**";

	/** 운영자 경로 — 운영자 체인이 맡고 명세 설정이 거부 응답을 단다 */
	public static final String ADMIN = "/api/admin/**";

	private ApiPaths() {
	}

}
