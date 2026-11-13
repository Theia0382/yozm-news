package net.yozmnews.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호를 바꿔야 하는 세션 사용자에게도 여는 화면용 API 처리기(클래스나 메서드) — docs/specs/platform.md §3 · platform-R21.
 * <p>
 * 세션 체인은 남이 정한 초기 비밀번호로 로그인한 세션 사용자의 요청을 공개 처리기와 이 표시가 있는 처리기에만 연다. 이 표시는 세션을 요구하는
 * 처리기의 것이다 — 공개 처리기에는 달지 않는다.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AllowedBeforePasswordChange {

	/** 비밀번호를 바꾸기 전에도 여는 이유 — 비울 수 없다 */
	String reason();

}
