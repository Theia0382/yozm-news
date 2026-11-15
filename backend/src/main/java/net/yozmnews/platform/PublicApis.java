package net.yozmnews.platform;

import java.lang.annotation.Annotation;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * 처리기 표시의 판정 정본 — 메서드나 그 클래스에 {@link PublicApi} 가 있으면 공개, {@link AllowedBeforePasswordChange} 가 있으면 비밀번호를 바꾸기
 * 전에도 열고, {@link AllowedWithoutLlmKey} 가 있으면 LLM 키가 없어도 연다. docs/specs/platform.md §3 · platform-R21
 */
public final class PublicApis {

	private PublicApis() {
	}

	/** 처리기(메서드나 그 클래스)에 공개 표시가 있는가 */
	public static boolean isPublic(HandlerMethod handler) {
		return marked(handler, PublicApi.class);
	}

	/** 처리기(메서드나 그 클래스)에 비밀번호를 바꾸기 전에도 여는 표시가 있는가 */
	public static boolean allowedBeforePasswordChange(HandlerMethod handler) {
		return marked(handler, AllowedBeforePasswordChange.class);
	}

	/** 처리기(메서드나 그 클래스)에 LLM 키가 없어도 여는 표시가 있는가 */
	public static boolean allowedWithoutLlmKey(HandlerMethod handler) {
		return marked(handler, AllowedWithoutLlmKey.class);
	}

	private static boolean marked(HandlerMethod handler, Class<? extends Annotation> marker) {
		return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), marker)
				|| AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), marker);
	}

}
