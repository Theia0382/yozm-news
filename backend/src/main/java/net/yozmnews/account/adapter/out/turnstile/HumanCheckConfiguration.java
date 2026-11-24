package net.yozmnews.account.adapter.out.turnstile;

import java.util.Objects;

import net.yozmnews.account.domain.humancheck.HumanCheckSetting;
import net.yozmnews.account.domain.humancheck.HumanVerifier;
import net.yozmnews.platform.SecretValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 봇 차단의 결선 — 설정 값의 판정과, 비밀 키가 있을 때의 Turnstile 검증기. 두 역할 공통이다. docs/specs/account.md §7 · account-R33
 * <p>
 * 비밀 키가 없을 때의 검증기는 로컬 대체물 결선(profile {@code local})이 정하고, 봇 차단을 켰는데 검증기가 없으면 봇 차단 유스케이스가 기동을
 * 멈춘다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HumanCheckProperties.class)
class HumanCheckConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(HumanCheckConfiguration.class);

	/**
	 * 봇 차단 설정 — 판정이 틀리면 기동하지 않는다.
	 * @throws IllegalStateException 모르는 공급자이거나 Turnstile 에 사이트 키가 없을 때 — 문구에 값이 없다
	 */
	@Bean
	HumanCheckSetting humanCheckSetting(HumanCheckProperties properties) {
		HumanCheckSetting setting;
		try {
			setting = HumanCheckSetting.of(properties.provider(), properties.siteKey());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(ex.getMessage(), ex);
		}
		switch (setting) {
			case HumanCheckSetting.Off off -> logger.info("봇 차단을 끈다 — 요청 제한만 건다");
			case HumanCheckSetting.Turnstile turnstile -> logger.info("봇 차단을 Turnstile 로 켠다");
		}
		return setting;
	}

	@Bean(destroyMethod = "close")
	@Conditional(TurnstileSecretCondition.Present.class)
	HumanVerifier turnstileHumanVerifier(HumanCheckProperties properties) {
		return new TurnstileHumanVerifier(
				SecretValue.of(Objects.requireNonNull(properties.secretKey(), TurnstileSecretCondition.PROPERTY)));
	}

}
