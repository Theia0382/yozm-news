package net.yozmnews.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 채팅 모델의 로거 — 앱 설정(application.yaml)을 읽어 기동하면 두 채팅 모델 클래스의 로그가 꺼져 있다. 두 클래스는 내용 없는
 * 응답에서 프롬프트 전체를 경고 로그에 남긴다.
 * <p>
 * 지키는 규칙: llm-R24
 */
class ChatModelLogLevelTests {

	@Test
	void theApplicationSettingsTurnOffBothChatModelLoggers() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NoBeans.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.run()) {
			LoggingSystem logging = context.getBean(LoggingSystem.class);

			assertThat(logging.getLoggerConfiguration("org.springframework.ai.openai.OpenAiChatModel").getEffectiveLevel())
				.isEqualTo(LogLevel.OFF);
			assertThat(logging.getLoggerConfiguration("org.springframework.ai.anthropic.AnthropicChatModel")
				.getEffectiveLevel()).isEqualTo(LogLevel.OFF);
			// 대조 — 설정을 읽었고, 끈 것은 두 클래스뿐이다
			assertThat(logging.getLoggerConfiguration("org.springframework.ai").getEffectiveLevel())
				.isNotEqualTo(LogLevel.OFF);
		}
	}

	/** 앱 설정만 읽고 빈은 올리지 않는다 — 기동 때의 로깅 설정만 본다 */
	@Configuration(proxyBeanMethods = false)
	static class NoBeans {

	}

}
