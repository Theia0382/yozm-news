package net.yozmnews.platform.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import net.yozmnews.platform.WebRole;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 화면용 API 명세의 고정 값과 속성 규칙 — docs/specs/platform.md §4 · platform-R6
 */
@WebRole
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	@Bean
	OpenAPI screenApi() {
		return new OpenAPI().info(new Info().title("요즘뉴스 화면용 API").version("0"))
			.servers(List.of(new Server().url("/")));
	}

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
