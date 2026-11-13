package net.yozmnews.user.application;

import java.util.Objects;

import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;

/**
 * 만료 세션 정리 유스케이스 — docs/specs/user.md §4 · user-R12. Spring Session 의 자체 정리 작업은 끄고 작업 역할의 반복 작업이 이것을 부른다.
 */
@Service
public class SessionExpiry {

	private final JdbcIndexedSessionRepository sessions;

	SessionExpiry(JdbcIndexedSessionRepository sessions) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
	}

	/** 만료된 세션을 지운다 */
	public void cleanUp() {
		this.sessions.cleanUpExpiredSessions();
	}

}
