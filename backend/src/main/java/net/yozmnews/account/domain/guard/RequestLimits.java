package net.yozmnews.account.domain.guard;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import net.yozmnews.platform.GuardedAction;

/**
 * 요청 제한 정책 표 — 동작마다 규칙의 정본. docs/specs/user.md §4 · user-R38
 * <p>
 * 값은 측정 전 목표값이다. 로그인 · 복구의 사용자 축은 이메일별 실패 지연이 맡으므로 여기서는 클라이언트 주소로만 센다.
 */
public final class RequestLimits {

	private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

	private static final Duration HOUR = Duration.ofHours(1);

	private RequestLimits() {
	}

	/** 이 동작의 규칙 — 요청 하나가 모든 규칙에서 센다 */
	public static List<LimitRule> rulesOf(GuardedAction action) {
		Objects.requireNonNull(action, "action");
		return switch (action) {
			case SIGN_IN -> List.of(new LimitRule(LimitAxis.CLIENT_ADDRESS, 30, TEN_MINUTES));
			case ACCOUNT_RECOVERY -> List.of(new LimitRule(LimitAxis.CLIENT_ADDRESS, 10, HOUR));
			case INVITATION_CHECK -> List.of(new LimitRule(LimitAxis.CLIENT_ADDRESS, 30, HOUR));
			case INVITATION_ACCEPTANCE -> List.of(new LimitRule(LimitAxis.CLIENT_ADDRESS, 10, HOUR));
			case SIGN_UP -> List.of(new LimitRule(LimitAxis.CLIENT_ADDRESS, 5, HOUR));
			case LLM_KEY_CHECK -> List.of(new LimitRule(LimitAxis.SESSION_USER, 20, HOUR),
					new LimitRule(LimitAxis.CLIENT_ADDRESS, 60, HOUR));
			// 다이제스트 하나의 항목(많아야 최대 후보 수 — 직접 입력 상한 100, 상한을 낮추기 전에 저장된 값은 150 까지)을 누르고 고쳐 누르는
			// 사용이 창 하나에 든다 — 항목마다 다섯 번까지 덮는다
			case FEEDBACK -> List.of(new LimitRule(LimitAxis.SESSION_USER, 750, TEN_MINUTES));
		};
	}

}
