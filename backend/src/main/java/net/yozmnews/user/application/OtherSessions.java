package net.yozmnews.user.application;

import java.util.Objects;

import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

/**
 * 한 사용자의 세션 지우기 — 세션 저장소의 사용자 이름 색인(사용자 ID 의 문자열)으로 찾는다. docs/specs/user.md §4 · user-R7
 * <p>
 * 세션 저장소는 부르는 쪽의 트랜잭션에 들지 않고 자기 트랜잭션으로 곧바로 커밋한다. 그래서 자격을 바꾸는 유스케이스는 이것을 커밋 직전의 마지막
 * 단계로 부른다 — 지우기가 실패하면 예외로 자격 변경이 롤백되고, 지운 뒤 커밋이 실패하면 세션만 끊긴다.
 */
@Component
class OtherSessions {

	private final JdbcIndexedSessionRepository sessions;

	OtherSessions(JdbcIndexedSessionRepository sessions) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
	}

	/** 이 사용자의 세션을 모두 지운다 */
	void endAll(UserId user) {
		end(user, null);
	}

	/**
	 * 이 세션을 뺀 이 사용자의 세션을 지운다.
	 * @param keepSessionId 세션 저장소에 지금 있는 ID — 요청 안에서 세션 ID 를 바꿨으면 바꾸기 전 ID 다(바꾼 ID 는 요청 끝에서야 저장된다)
	 */
	void endAllExcept(UserId user, String keepSessionId) {
		end(user, Objects.requireNonNull(keepSessionId, "keepSessionId"));
	}

	private void end(UserId user, @Nullable String keep) {
		this.sessions.findByPrincipalName(Long.toString(user.value()))
			.keySet()
			.stream()
			.filter(id -> !id.equals(keep))
			.forEach(this.sessions::deleteById);
	}

	@Override
	public String toString() {
		return "OtherSessions";
	}

}
