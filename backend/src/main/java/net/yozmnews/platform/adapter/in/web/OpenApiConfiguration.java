package net.yozmnews.platform.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.core.converter.ModelConverter;
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
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.ApiPaths;
import net.yozmnews.platform.HandlerGuards;
import net.yozmnews.platform.HumanChecked;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.PublicApis;
import net.yozmnews.platform.RequestLimited;
import net.yozmnews.platform.WebRole;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
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
 * 화면용 API 명세의 고정 값과 속성 규칙, 운영자 경로 · 세션 경로 연산의 거부 응답, 스키마 이름 겹침 검사 — docs/specs/platform.md §4 ·
 * platform-R6
 */
@WebRole
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	/** 문제 응답 스키마 이름 — {@code ApiProblem} */
	private static final String PROBLEM_SCHEMA = "ApiProblem";

	/** CSRF 토큰을 요구하는 변경 메서드 */
	private static final Set<RequestMethod> UNSAFE_METHODS = Set.of(RequestMethod.POST, RequestMethod.PUT,
			RequestMethod.PATCH, RequestMethod.DELETE);

	/** 401 — 세션 사용자가 없다(운영자 경로 · 세션 경로 공통) */
	private static final String NO_SESSION = "세션이 없거나 만료됐다";

	/** 403 — 변경 요청의 CSRF 토큰이 없거나 틀리다(운영자 경로 · 세션 경로 공통) */
	private static final String CSRF_DENIAL = "요청 보호 토큰이 없거나 맞지 않는다";

	/** 403 — 세션 사용자가 비밀번호를 바꿔야 한다(운영자 경로 · 세션 경로 공통) */
	private static final String PASSWORD_CHANGE_DENIAL = "비밀번호를 바꿔야 한다";

	@Bean
	OpenAPI screenApi() {
		return new OpenAPI().info(new Info().title("요즘뉴스 화면용 API").version("0"))
			.servers(List.of(new Server().url("/")));
	}

	/** 스키마 이름 겹침 검사의 변환기 — springdoc 이 변환기 사슬에 넣는다. docs/specs/platform.md §4 · platform-R128 */
	@Bean
	ModelConverter schemaNameCheck() {
		return new SchemaNameCheck();
	}

	/** 명세 생성마다 스키마 이름 기록을 새로 시작한다 — platform-R128 */
	@Bean
	OpenApiBuilderCustomizer schemaNameRecording() {
		return service -> SchemaNameCheck.start();
	}

	/**
	 * 명세 생성 끝에서 스키마 이름 기록을 대조한다 — platform-R128
	 * @throws IllegalStateException 같은 스키마 이름을 서로 다른 타입이 받았을 때, 변환기가 보지 못한 스키마가 있을 때
	 */
	@Bean
	OpenApiCustomizer schemaNameCollisions() {
		return openApi -> SchemaNameCheck.finish(openApi.getComponents());
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
	 * 운영자 경로의 모든 연산에 401 · 403 문제 응답을 단다 — 401 은 세션 없음 · 틀린 운영자 토큰, 403 은 변경 메서드면 CSRF 와 비밀번호
	 * 변경 필요 · 운영자 아님(서버의 판정 순서). 운영자 경로는 {@link ApiPaths#ADMIN} 가 정본이다. docs/specs/platform.md §4 ·
	 * platform-R6
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
				item.readOperationsMap().forEach((method, operation) -> {
					reject(operation, path, "401", NO_SESSION + " · 운영자 토큰(Bearer)이 맞지 않는다");
					List<String> denials = new ArrayList<>();
					if (UNSAFE_METHODS.contains(RequestMethod.valueOf(method.name()))) {
						denials.add(CSRF_DENIAL);
					}
					denials.add(PASSWORD_CHANGE_DENIAL);
					denials.add("운영자 권한이 없다");
					reject(operation, path, "403", String.join(" · ", denials));
				});
			});
		};
	}

	/**
	 * 운영자 경로 밖 화면용 API 연산의 거부 응답 — 처리기에 {@link PublicApi} 가 없으면 401(세션 없음), 변경 메서드면 공개 처리기여도
	 * 403(CSRF), 공개도 {@link AllowedBeforePasswordChange} 도 아니면 403(비밀번호를 바꿔야 한다), 공개도 {@link AllowedWithoutLlmKey} 도
	 * 아니면 403(LLM 키를 등록해야 한다), {@link HumanChecked} 가 있으면 403(사람 확인 실패), {@link RequestLimited} 가 있으면 429(요청이
	 * 너무 많다). 403 은 연산마다 하나라 까닭이 겹치면 한 설명에 {@code  · } 로 이어 적는다. 처리기의 경로 · 메서드는 처리기 매핑에서 읽는다.
	 * docs/specs/platform.md §4 · platform-R6 · platform-R21 · platform-R25
	 * @throws IllegalStateException 연산이 401 · 403 · 429 를 이미 가질 때(정본이 둘이 된다)
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
				reject(operation, path, "401", NO_SESSION);
			}
			boolean changes = mappings.stream()
				.flatMap(mapping -> mapping.getMethodsCondition().getMethods().stream())
				.anyMatch(UNSAFE_METHODS::contains);
			List<String> denials = new ArrayList<>();
			if (changes) {
				denials.add(CSRF_DENIAL);
			}
			if (!PublicApis.isPublic(handler) && !PublicApis.allowedBeforePasswordChange(handler)) {
				denials.add(PASSWORD_CHANGE_DENIAL);
			}
			if (!PublicApis.isPublic(handler) && !PublicApis.allowedWithoutLlmKey(handler)) {
				denials.add("LLM 키를 등록해야 한다");
			}
			if (HandlerGuards.humanChecked(handler).isPresent()) {
				denials.add("사람 확인에 실패했다(봇 차단을 켠 인스턴스)");
			}
			if (!denials.isEmpty()) {
				reject(operation, path, "403", String.join(" · ", denials));
			}
			if (HandlerGuards.requestLimited(handler).isPresent()) {
				reject(operation, path, "429", "요청이 너무 많다 — Retry-After 뒤에 다시 시도한다");
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
