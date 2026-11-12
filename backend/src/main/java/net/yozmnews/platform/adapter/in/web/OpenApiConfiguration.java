package net.yozmnews.platform.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import net.yozmnews.platform.ApiPaths;
import net.yozmnews.platform.WebRole;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 화면용 API 명세의 고정 값과 속성 규칙, 운영자 경로의 거부 응답 — docs/specs/platform.md §4 · platform-R6
 */
@WebRole
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	/** 문제 응답 스키마 이름 — {@code ApiProblem} */
	private static final String PROBLEM_SCHEMA = "ApiProblem";

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

	/**
	 * 운영자 경로의 모든 연산에 401 · 403 문제 응답을 단다 — 운영자 경로는 {@link ApiPaths#ADMIN} 가 정본이다.
	 * @throws IllegalStateException 연산이 401 · 403 을 이미 가질 때(정본이 둘이 된다), 문제 응답 스키마가 없을 때
	 */
	@Bean
	OpenApiCustomizer adminResponses() {
		PathPattern adminPaths = PathPatternParser.defaultInstance.parse(ApiPaths.ADMIN);
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((path, item) -> {
				if (!adminPaths.matches(PathContainer.parsePath(path))) {
					return;
				}
				Components components = openApi.getComponents();
				if (components == null || components.getSchemas() == null
						|| !components.getSchemas().containsKey(PROBLEM_SCHEMA)) {
					throw new IllegalStateException("운영자 경로의 거부 응답이 가리킬 스키마가 없다: " + PROBLEM_SCHEMA);
				}
				for (Operation operation : item.readOperations()) {
					reject(operation, path, "401", "운영자 토큰이 없거나 맞지 않는다");
					reject(operation, path, "403", "운영자 기능이 닫혀 있다");
				}
			});
		};
	}

	private static void reject(Operation operation, String path, String status, String description) {
		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			responses = new ApiResponses();
			operation.setResponses(responses);
		}
		if (responses.containsKey(status)) {
			throw new IllegalStateException("운영자 경로의 " + status + " 응답은 명세 설정이 단다 — 컨트롤러에서 지운다: " + path);
		}
		responses.addApiResponse(status, new ApiResponse().description(description)
			.content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
	}

}
