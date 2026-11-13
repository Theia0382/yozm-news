package net.yozmnews.platform.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.ApiPaths;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.PublicApis;
import net.yozmnews.platform.WebRole;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 화면용 API 명세의 고정 값과 속성 규칙, 운영자 경로 · 세션 경로 연산의 거부 응답 — docs/specs/platform.md §4 · platform-R6
 */
@WebRole
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	/** 문제 응답 스키마 이름 — {@code ApiProblem} */
	private static final String PROBLEM_SCHEMA = "ApiProblem";

	/** CSRF 토큰을 요구하는 변경 메서드 */
	private static final Set<RequestMethod> UNSAFE_METHODS = Set.of(RequestMethod.POST, RequestMethod.PUT,
			RequestMethod.PATCH, RequestMethod.DELETE);

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

	/**
	 * 운영자 경로 밖 화면용 API 연산의 거부 응답 — 처리기에 {@link PublicApi} 가 없으면 401(세션 없음), 변경 메서드면 공개 처리기여도
	 * 403(CSRF), 공개도 {@link AllowedBeforePasswordChange} 도 아니면 403(비밀번호를 바꿔야 한다). 403 은 연산마다 하나라 둘이 겹치면 한
	 * 설명에 함께 적는다. 처리기의 경로 · 메서드는 처리기 매핑에서 읽는다. docs/specs/platform.md §4 · platform-R6 · platform-R21
	 * @throws IllegalStateException 연산이 401 · 403 을 이미 가질 때(정본이 둘이 된다)
	 */
	@Bean
	OperationCustomizer sessionResponses(
			@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
		PathPattern adminPaths = PathPatternParser.defaultInstance.parse(ApiPaths.ADMIN);
		return (operation, handler) -> {
			List<RequestMappingInfo> mappings = handlerMapping.getHandlerMethods()
				.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getMethod().equals(handler.getMethod()))
				.map(Map.Entry::getKey)
				.toList();
			List<String> paths = mappings.stream()
				.flatMap(mapping -> mapping.getPatternValues().stream())
				.toList();
			if (paths.isEmpty() || paths.stream().anyMatch(path -> adminPaths.matches(PathContainer.parsePath(path)))) {
				return operation;
			}
			String path = paths.getFirst();
			if (!PublicApis.isPublic(handler)) {
				reject(operation, path, "401", "세션이 없거나 만료됐다");
			}
			boolean changes = mappings.stream()
				.flatMap(mapping -> mapping.getMethodsCondition().getMethods().stream())
				.anyMatch(UNSAFE_METHODS::contains);
			boolean beforePasswordChange = PublicApis.isPublic(handler)
					|| PublicApis.allowedBeforePasswordChange(handler);
			if (changes && beforePasswordChange) {
				reject(operation, path, "403", "요청 보호 토큰이 없거나 맞지 않는다");
			}
			else if (changes) {
				reject(operation, path, "403", "요청 보호 토큰이 없거나 맞지 않거나, 비밀번호를 바꿔야 한다");
			}
			else if (!beforePasswordChange) {
				reject(operation, path, "403", "비밀번호를 바꿔야 한다");
			}
			return operation;
		};
	}

	private static void reject(Operation operation, String path, String status, String description) {
		ApiResponses responses = operation.getResponses();
		if (responses == null) {
			responses = new ApiResponses();
			operation.setResponses(responses);
		}
		if (responses.containsKey(status)) {
			throw new IllegalStateException("거부 응답 " + status + " 는 명세 설정이 단다 — 컨트롤러에서 지운다: " + path);
		}
		responses.addApiResponse(status, new ApiResponse().description(description)
			.content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
	}

}
