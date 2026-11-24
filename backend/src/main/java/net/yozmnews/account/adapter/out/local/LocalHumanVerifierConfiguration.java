package net.yozmnews.account.adapter.out.local;

import net.yozmnews.account.adapter.out.turnstile.TurnstileSecretCondition;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import net.yozmnews.platform.LocalDevelopment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 개발의 항상 통과 봇 차단 검증기 결선 — 로컬 개발에서 Turnstile 비밀 키가 없을 때만 등록한다. docs/specs/account.md §7 · account-R33 ·
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
