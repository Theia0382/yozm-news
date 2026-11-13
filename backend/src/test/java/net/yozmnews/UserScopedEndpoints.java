package net.yozmnews;

import java.util.Map;

import net.yozmnews.user.NoticeScopeTests;

/**
 * 사용자 데이터를 다루는 화면용 API 처리기와 그 격리 시험 — 운영자 경로 밖의 처리기는 공개 표시가 없으면 여기 한 줄로 등록한다. 키는
 * {@code "메서드 경로"}(매핑 그대로, 예 {@code "GET /api/digests/{date}"}), 값은 사용자 A 의 세션으로 B 의 데이터에 닿지 않음을 보는 시험
 * 클래스다. 등록 가드는 {@link UserScopeTests}, 규범은 docs/qa/test-strategy.md §13.
 * <p>
 * 지키는 규칙: user-R6
 */
public final class UserScopedEndpoints {

	/** 등록 — 사용자 데이터 처리기를 더하는 작업이 같은 변경에 격리 시험과 함께 한 줄을 더한다 */
	public static final Map<String, Class<?>> ENDPOINTS = Map.of("GET /api/notices", NoticeScopeTests.class,
			"GET /api/notices/unread-count", NoticeScopeTests.class, "POST /api/notices/read", NoticeScopeTests.class);

	private UserScopedEndpoints() {
	}

}
