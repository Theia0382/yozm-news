package net.yozmnews.platform;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * 처리기가 공개 처리기인지의 판정 정본 — 메서드나 그 클래스에 {@link PublicApi} 가 있으면 공개다. docs/specs/platform.md §3 · platform-R21
 */
public final class PublicApis {

	private PublicApis() {
	}

	/** 처리기(메서드나 그 클래스)에 공개 표시가 있는가 */
	public static boolean isPublic(HandlerMethod handler) {
		return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PublicApi.class)
				|| AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PublicApi.class);
	}

}
