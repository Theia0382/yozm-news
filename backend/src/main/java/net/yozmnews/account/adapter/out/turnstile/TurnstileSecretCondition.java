package net.yozmnews.account.adapter.out.turnstile;

import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Turnstile 비밀 키가 설정됐는가 — 정본. 비어 있지 않은 {@value #PROPERTY} 다. Turnstile 결선과 로컬 대체물 결선이 함께 읽는다 —
 * docs/specs/account.md §7 · account-R33.
 * <p>
 * 빈 값(예: 값을 비운 환경 변수 줄)은 비밀 키가 없는 것이다.
 */
public final class TurnstileSecretCondition {

	/** 비밀 키의 설정 이름 — 정본은 {@link HumanCheckSetting#SECRET_KEY_PROPERTY} */
	public static final String PROPERTY = HumanCheckSetting.SECRET_KEY_PROPERTY;

	private TurnstileSecretCondition() {
	}

	/** 비밀 키가 설정됐는가 */
	public static boolean configured(Environment environment) {
		String value = environment.getProperty(PROPERTY);
		return value != null && !value.isEmpty();
	}

	/** 비밀 키가 있을 때 */
	public static final class Present implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return configured(context.getEnvironment());
		}

	}

	/** 비밀 키가 없을 때 */
	public static final class Absent implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return !configured(context.getEnvironment());
		}

	}

}
