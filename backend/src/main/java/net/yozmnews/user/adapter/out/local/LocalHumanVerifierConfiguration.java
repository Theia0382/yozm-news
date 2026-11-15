package net.yozmnews.user.adapter.out.local;

import net.yozmnews.platform.LocalDevelopment;
import net.yozmnews.user.adapter.out.turnstile.TurnstileSecretCondition;
import net.yozmnews.user.domain.humancheck.HumanVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 개발의 항상 통과 봇 차단 검증기 결선 — 로컬 개발에서 Turnstile 비밀 키가 없을 때만 등록한다. docs/specs/user.md §7 · user-R41 ·
 * platform-R18
 */
@LocalDevelopment
@Conditional(TurnstileSecretCondition.Absent.class)
@Configuration(proxyBeanMethods = false)
class LocalHumanVerifierConfiguration {

	@Bean
	HumanVerifier alwaysPassHumanVerifier() {
		return new AlwaysPassHumanVerifier();
	}

}
