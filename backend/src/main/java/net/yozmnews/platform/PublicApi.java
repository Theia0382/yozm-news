package net.yozmnews.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 세션 없이 여는 화면용 API 처리기(클래스나 메서드) — docs/specs/platform.md §3 · platform-R21
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {

	/** 공개인 이유 — 비울 수 없다 */
	String reason();

}
