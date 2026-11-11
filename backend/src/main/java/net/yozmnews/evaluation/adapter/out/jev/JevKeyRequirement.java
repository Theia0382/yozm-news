package net.yozmnews.evaluation.adapter.out.jev;

import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.platform.WorkerRole;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 작업 역할의 기동 검사 — 작업 역할은 평가기 없이 운영하지 않는다(ADR-0061) · docs/specs/evaluation.md evaluation-R43.
 * <p>
 * 키가 없으면 로컬 개발(profile {@code local})이 아닌 한 작업 역할이 뜨지 않는다. 화면 역할만 띄운 프로세스는 키를 요구하지 않는다. 키가
 * 맞는지는 평가 때 드러난다.
 */
@WorkerRole
@Component
class JevKeyRequirement {

	static final String KEY_MISSING = "Jev API 키가 없어 작업 역할을 띄울 수 없다 — " + JevKeyCondition.PROPERTY
			+ "(환경 변수 " + JevKeyCondition.ENVIRONMENT_VARIABLE + ")를 설정한다";

	/**
	 * @throws IllegalStateException 로컬 개발이 아닌데 키가 없을 때
	 */
	JevKeyRequirement(Environment environment) {
		if (!JevKeyCondition.configured(environment) && !environment.matchesProfiles(LocalDevelopment.PROFILE)) {
			throw new IllegalStateException(KEY_MISSING);
		}
	}

}
