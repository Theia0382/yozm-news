package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 만료 세션은 작업 역할의 반복 작업만 지운다 — Spring Session 의 저장소가 자체 정리 스케줄러를 만들지 않는다. 저장소는 정리 주기가 꺼짐
 * ({@code "-"})이 아니면 기동 때 스케줄러(필드 {@code taskScheduler})를 만들므로, 그 필드가 비어 있음을 본다. 라이브러리가 필드 이름을 바꾸면
 * 이 시험이 실패한다.
 * <p>
 * 지키는 규칙: user-R12
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SessionCleanupTests {

	@Autowired
	JdbcIndexedSessionRepository sessions;

	@Test
	void sessionRepositoryDoesNotScheduleItsOwnCleanup() {
		assertThat(ReflectionTestUtils.getField(this.sessions, "taskScheduler")).isNull();
	}

}
