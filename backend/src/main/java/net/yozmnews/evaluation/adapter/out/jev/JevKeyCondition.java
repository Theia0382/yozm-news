package net.yozmnews.evaluation.adapter.out.jev;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Jev 키가 설정됐는가 — 정본. 비어 있지 않은 {@value #PROPERTY} 다. Jev 결선 · 로컬 대체물 결선 · 작업 역할 기동 검사가 함께 읽는다 —
 * docs/specs/evaluation.md §7.
 * <p>
 * 빈 값(예: 값을 비운 환경 변수 줄)은 키가 없는 것이다. 공백뿐인 값은 키가 있는 것이고, 형식 검사가 기동을 막는다.
 */
public final class JevKeyCondition {

	/** 키의 설정 이름 */
	public static final String PROPERTY = "yozmnews.jev.api-key";

	/** 키의 환경 변수 이름 — 운영자 문구에 설정 이름과 함께 쓴다 */
	static final String ENVIRONMENT_VARIABLE = "YOZMNEWS_JEV_API_KEY";

	private JevKeyCondition() {
	}

	/** 키가 설정됐는가 */
	public static boolean configured(Environment environment) {
		String value = environment.getProperty(PROPERTY);
		return value != null && !value.isEmpty();
	}

	/** 키가 있을 때 */
	public static final class Present implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return configured(context.getEnvironment());
		}

	}

	/** 키가 없을 때 */
	public static final class Absent implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return !configured(context.getEnvironment());
		}

	}

}
