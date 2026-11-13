package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 사용자 격리 등록 가드 — 앱의 모든 화면용 API 처리기(운영자 경로 밖)가 공개 표시나 격리 시험 등록 가운데 꼭 하나를 갖고, 사용자 ID 를 요청에서
 * 받지 않는다. 규범은 docs/qa/test-strategy.md §13.
 * <p>
 * 지키는 규칙: user-R5 · user-R6
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserScopeTests {

	/** 이 수보다 적게 열거하면 열거 자체가 깨진 것이다 — 배포 확인 · 로그인 · 로그아웃 · 로그인 상태 조회 */
	private static final int MINIMUM_ENDPOINTS = 4;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mapping;

	@Test
	void everyScreenApiHandlerIsPublicOrRegisteredAndTakesNoUserIdFromTheRequest() {
		UserScopeRules.Verdict verdict = UserScopeRules.check(this.mapping, UserScopedEndpoints.ENDPOINTS);

		assertThat(verdict.endpoints()).as("열거한 처리기").hasSizeGreaterThanOrEqualTo(MINIMUM_ENDPOINTS)
			.contains("GET /api/ping", "POST /api/session", "DELETE /api/session", "GET /api/me");
		assertThat(verdict.violations()).isEmpty();
	}

}
