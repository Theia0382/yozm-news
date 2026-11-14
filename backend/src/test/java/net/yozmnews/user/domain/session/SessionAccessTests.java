package net.yozmnews.user.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

/**
 * 세션 체인의 인가 판정 — 처리기의 표시 · 세션 사용자 · 강제 변경 · 키 유무의 조합을 손으로 적은 표와 견주고, 키 유무를 묻지 않아야 하는
 * 갈래에서 묻지 않음을 센다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: user-R5
 */
class SessionAccessTests {

	private static final HandlerMarks PLAIN = new HandlerMarks(true, false, false, false);

	private static final HandlerMarks PUBLIC = new HandlerMarks(true, true, false, false);

	private static final HandlerMarks BEFORE_CHANGE = new HandlerMarks(true, false, true, false);

	private static final HandlerMarks WITHOUT_KEY = new HandlerMarks(true, false, false, true);

	private static final HandlerMarks BOTH = new HandlerMarks(true, false, true, true);

	private final AtomicInteger asked = new AtomicInteger();

	@Test
	void opensRequestsWithoutAHandlerAndPublicHandlersWithoutAsking() {
		assertThat(SessionAccess.decide(HandlerMarks.NO_HANDLER, null, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(SessionAccess.decide(PUBLIC, null, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(SessionAccess.decide(PUBLIC, true, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(SessionAccess.decide(PUBLIC, false, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(this.asked).hasValue(0);
	}

	@Test
	void needsASessionUserForEveryOtherHandler() {
		assertThat(SessionAccess.decide(PLAIN, null, key(true))).isEqualTo(SessionAccess.UNAUTHENTICATED);
		assertThat(SessionAccess.decide(BEFORE_CHANGE, null, key(true))).isEqualTo(SessionAccess.UNAUTHENTICATED);
		assertThat(SessionAccess.decide(WITHOUT_KEY, null, key(true))).isEqualTo(SessionAccess.UNAUTHENTICATED);
		assertThat(this.asked).hasValue(0);
	}

	@Test
	void aUserWhoMustChangeThePasswordIsJudgedByThatMarkAloneBeforeTheKey() {
		assertThat(SessionAccess.decide(PLAIN, true, key(false))).isEqualTo(SessionAccess.PASSWORD_CHANGE_REQUIRED);
		assertThat(SessionAccess.decide(PLAIN, true, key(true))).isEqualTo(SessionAccess.PASSWORD_CHANGE_REQUIRED);
		// 키 없이 허용 표시는 강제 변경을 열지 않는다
		assertThat(SessionAccess.decide(WITHOUT_KEY, true, key(true)))
			.isEqualTo(SessionAccess.PASSWORD_CHANGE_REQUIRED);
		// 변경 전 허용 표시는 키가 없어도 연다
		assertThat(SessionAccess.decide(BEFORE_CHANGE, true, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(SessionAccess.decide(BOTH, true, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(this.asked).hasValue(0);
	}

	@Test
	void aUserWithoutAKeyReachesOnlyHandlersOpenWithoutAKey() {
		assertThat(SessionAccess.decide(PLAIN, false, key(false))).isEqualTo(SessionAccess.LLM_KEY_REQUIRED);
		// 변경 전 허용 표시는 키 관문을 열지 않는다
		assertThat(SessionAccess.decide(BEFORE_CHANGE, false, key(false))).isEqualTo(SessionAccess.LLM_KEY_REQUIRED);
		assertThat(this.asked).hasValue(2);
	}

	@Test
	void handlersOpenWithoutAKeyOpenWithoutAsking() {
		assertThat(SessionAccess.decide(WITHOUT_KEY, false, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(SessionAccess.decide(BOTH, false, key(false))).isEqualTo(SessionAccess.OPEN);
		assertThat(this.asked).hasValue(0);
	}

	@Test
	void aUserWithAKeyReachesEveryHandler() {
		assertThat(SessionAccess.decide(PLAIN, false, key(true))).isEqualTo(SessionAccess.OPEN);
		assertThat(SessionAccess.decide(BEFORE_CHANGE, false, key(true))).isEqualTo(SessionAccess.OPEN);
		assertThat(this.asked).hasValue(2);
	}

	private BooleanSupplier key(boolean present) {
		return () -> {
			this.asked.incrementAndGet();
			return present;
		};
	}

}
