package net.yozmnews.platform;

import java.util.Optional;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * 처리기의 요청 방어 표시 판정 정본 — 처리기 메서드에 {@link RequestLimited} 가 있으면 그 동작으로 요청 제한을, {@link HumanChecked} 가 있으면
 * 그 동작으로 봇 차단을 건다. docs/specs/platform.md §3 · platform-R25
 * <p>
 * 표시는 메서드에만 달리므로 메서드만 읽는다.
 */
public final class HandlerGuards {

	private HandlerGuards() {
	}

	/** 요청 제한을 거는 동작 — 표시가 없으면 비어 있다 */
	public static Optional<GuardedAction> requestLimited(HandlerMethod handler) {
		return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestLimited.class))
			.map(RequestLimited::value);
	}

	/** 봇 차단을 거는 동작 — 표시가 없으면 비어 있다 */
	public static Optional<GuardedAction> humanChecked(HandlerMethod handler) {
		return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), HumanChecked.class))
			.map(HumanChecked::value);
	}

}
