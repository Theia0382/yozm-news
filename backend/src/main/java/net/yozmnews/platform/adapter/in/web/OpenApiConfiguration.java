package net.yozmnews.platform.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 화면용 API 명세의 고정 값과 속성 규칙. 명세의 정본은 저장소 루트의 {@code api/openapi.json} 이고, 테스트가 이 설정으로
 * 만든 명세와 그 파일을 비교한다.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	/**
	 * 실행 환경에 따라 바뀌지 않는 {@code info} · {@code servers}. 화면용 API 는 프론트엔드와 함께 바뀌므로 버전을 두지
	 * 않고, SPA 와 같은 오리진에서 제공된다.
	 */
	@Bean
	OpenAPI screenApi() {
		return new OpenAPI().info(new Info().title("요즘뉴스 화면용 API").version("0"))
			.servers(List.of(new Server().url("/")));
	}

	/**
	 * 객체 스키마의 속성을 모두 필수로 낸다(이름 순). 응답은 null 인 속성도 키째 쓰므로 키는 항상 있다 — null 가능성은
	 * JSpecify {@code @Nullable} 이 타입으로 남긴다({@code application.yaml} 의 springdoc nullable 설정).
	 */
	@Bean
	OpenApiCustomizer requireAllProperties() {
		return openApi -> {
			Components components = openApi.getComponents();
			if (components == null || components.getSchemas() == null) {
				return;
			}
			for (Schema<?> schema : components.getSchemas().values()) {
				Map<String, Schema> properties = schema.getProperties();
				if (properties != null && !properties.isEmpty()) {
					schema.setRequired(new ArrayList<>(properties.keySet().stream().sorted().toList()));
				}
			}
		};
	}

}
