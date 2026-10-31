package net.yozmnews.platform.application;

import net.yozmnews.platform.WebRole;
import net.yozmnews.platform.WorkerRole;
import net.yozmnews.platform.domain.ActiveRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 역할 검사의 결선 — docs/specs/platform.md §4 · platform-R8
 * <p>
 * 역할은 켜진 profile 로 정한다 — 활성 profile 이 없으면 기본 profile 이다.
 */
@Configuration(proxyBeanMethods = false)
class RoleConfiguration {

	@Bean
	ActiveRoles activeRoles(Environment environment) {
		return new ActiveRoles(environment.matchesProfiles(WebRole.PROFILE),
				environment.matchesProfiles(WorkerRole.PROFILE));
	}

}
