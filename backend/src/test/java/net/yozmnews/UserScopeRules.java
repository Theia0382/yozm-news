package net.yozmnews;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.ApiPaths;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.PublicApis;
import net.yozmnews.platform.UserId;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 사용자 격리 등록 가드의 판정 — 앱의 처리기 매핑을 열거해, 운영자 경로 밖 화면용 API 처리기마다 공개 표시와 격리 시험 등록 가운데 꼭 하나가
 * 있는지, 등록이 낡지 않았는지, 사용자 ID 를 요청에서 받지 않는지, 비밀번호 변경 전 허용 표시가 세션 처리기에만 이유와 함께 달렸는지
 * 본다. {@code UserScopeTests} 가 앱에, 틀 자체의 시험이 일부러 틀린 시험 컨텍스트에 돌린다.
 * <p>
 * 지키는 규칙: user-R5 · user-R6 · platform-R21
 */
public final class UserScopeRules {

	private static final PathPattern API = PathPatternParser.defaultInstance.parse(ApiPaths.API);

	private static final PathPattern ADMIN = PathPatternParser.defaultInstance.parse(ApiPaths.ADMIN);

	/** 요청에서 값을 묶는 애너테이션 — 이 인자로 사용자 ID 를 받으면 안 된다 */
	private static final List<Class<? extends Annotation>> BINDINGS = List.of(PathVariable.class, RequestParam.class,
			RequestBody.class, ModelAttribute.class, RequestHeader.class, CookieValue.class, MatrixVariable.class,
			RequestPart.class);

	private static final String USER_ID_NAME = "userId";

	private UserScopeRules() {
	}

	/** 판정 결과 — 열거한 처리기(메서드 · 경로 키)와 어긴 것 */
	public record Verdict(Set<String> endpoints, List<String> violations) {
	}

	/**
	 * 처리기 매핑과 격리 시험 등록으로 판정한다.
	 * @param registry 메서드 · 경로 키({@code "GET /api/…"}) → 격리 시험 클래스
	 */
	public static Verdict check(RequestMappingHandlerMapping mapping, Map<String, Class<?>> registry) {
		Set<String> endpoints = new TreeSet<>();
		List<String> violations = new ArrayList<>();
		Set<String> registered = new HashSet<>();
		mapping.getHandlerMethods().forEach((info, handler) -> {
			for (String key : screenApiKeys(info)) {
				endpoints.add(key);
				boolean isPublic = PublicApis.isPublic(handler);
				boolean isRegistered = registry.containsKey(key);
				if (isRegistered) {
					registered.add(key);
				}
				if (isPublic == isRegistered) {
					violations.add(key + " — 공개 표시와 격리 시험 등록 가운데 꼭 하나가 있어야 한다(공개 " + isPublic + " · 등록 "
							+ isRegistered + ")");
				}
				if (isPublic && blankReason(handler, PublicApi.class, PublicApi::reason)) {
					violations.add(key + " — 공개 표시의 이유가 비었다");
				}
				if (PublicApis.allowedBeforePasswordChange(handler)) {
					if (isPublic) {
						violations.add(key + " — 공개 처리기에 비밀번호 변경 전 허용 표시를 달았다");
					}
					if (blankReason(handler, AllowedBeforePasswordChange.class, AllowedBeforePasswordChange::reason)) {
						violations.add(key + " — 비밀번호 변경 전 허용 표시의 이유가 비었다");
					}
				}
				if (PublicApis.allowedWithoutLlmKey(handler)) {
					if (isPublic) {
						violations.add(key + " — 공개 처리기에 키 없이 허용 표시를 달았다");
					}
					if (blankReason(handler, AllowedWithoutLlmKey.class, AllowedWithoutLlmKey::reason)) {
						violations.add(key + " — 키 없이 허용 표시의 이유가 비었다");
					}
				}
				violations.addAll(userIdInputs(key, handler));
			}
		});
		registry.forEach((key, test) -> {
			if (!registered.contains(key)) {
				violations.add(key + " — 등록했지만 그런 처리기가 없다(낡은 등록)");
			}
			if (test == null) {
				violations.add(key + " — 격리 시험 클래스가 없다");
			}
		});
		return new Verdict(endpoints, violations);
	}

	/** 운영자 경로 밖 화면용 API 의 메서드 · 경로 키 */
	private static List<String> screenApiKeys(RequestMappingInfo info) {
		List<String> keys = new ArrayList<>();
		for (String path : info.getPatternValues()) {
			PathContainer container = PathContainer.parsePath(path);
			if (!API.matches(container) || ADMIN.matches(container)) {
				continue;
			}
			if (info.getMethodsCondition().getMethods().isEmpty()) {
				keys.add("* " + path);
			}
			info.getMethodsCondition().getMethods().forEach(method -> keys.add(method.name() + " " + path));
		}
		return keys;
	}

	/** 처리기(메서드, 없으면 그 클래스)의 표시에 이유가 비었는가 — 표시가 없어도 빈 것으로 본다 */
	private static <A extends Annotation> boolean blankReason(HandlerMethod handler, Class<A> marker,
			Function<A, String> reason) {
		A annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), marker);
		if (annotation == null) {
			annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), marker);
		}
		return annotation == null || reason.apply(annotation).isBlank();
	}

	/** 사용자 ID 를 요청에서 받는 인자 — 사용자는 묶기 애너테이션 없는 {@link UserId} 로만 받는다(user-R6) */
	private static List<String> userIdInputs(String key, HandlerMethod handler) {
		List<String> violations = new ArrayList<>();
		for (MethodParameter parameter : handler.getMethodParameters()) {
			parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
			Class<?> type = parameter.getParameterType();
			boolean bound = BINDINGS.stream().anyMatch(parameter::hasParameterAnnotation);
			String where = key + " 의 인자 " + parameter.getParameterIndex();
			if (type == UserId.class) {
				if (parameter.getParameterAnnotations().length > 0) {
					violations.add(where + " — UserId 에 애너테이션을 달면 요청에서 묶인다");
				}
				continue;
			}
			// 묶기 애너테이션이 없는 레코드도 질의 · 폼 값으로 묶인다(모델 속성)
			if (!bound && !type.isRecord()) {
				continue;
			}
			if (USER_ID_NAME.equals(boundName(parameter))) {
				violations.add(where + " — 요청에서 이름 userId 를 받는다");
			}
			if (type.isRecord() && carriesUserId(type, new HashSet<>())) {
				violations.add(where + " — 요청 레코드가 사용자 ID(UserId · userId)를 담는다");
			}
		}
		return violations;
	}

	private static String boundName(MethodParameter parameter) {
		PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
		if (path != null && !path.name().isEmpty()) {
			return path.name();
		}
		RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
		if (param != null && !param.name().isEmpty()) {
			return param.name();
		}
		RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
		if (header != null && !header.name().isEmpty()) {
			return header.name();
		}
		CookieValue cookie = parameter.getParameterAnnotation(CookieValue.class);
		if (cookie != null && !cookie.name().isEmpty()) {
			return cookie.name();
		}
		String name = parameter.getParameterName();
		return (name != null) ? name : "";
	}

	private static boolean carriesUserId(Class<?> record, Set<Class<?>> seen) {
		if (!seen.add(record)) {
			return false;
		}
		for (RecordComponent component : record.getRecordComponents()) {
			Class<?> type = component.getType();
			if (type == UserId.class || USER_ID_NAME.equals(component.getName())) {
				return true;
			}
			if (type.isRecord() && carriesUserId(type, seen)) {
				return true;
			}
		}
		return false;
	}

}
