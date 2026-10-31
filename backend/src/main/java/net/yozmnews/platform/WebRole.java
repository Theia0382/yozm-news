package net.yozmnews.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Profile;

/**
 * 화면 역할(profile {@value #PROFILE})에서만 켜는 구성요소 — docs/specs/platform.md §3 · platform-R8 · platform-R9
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile(WebRole.PROFILE)
public @interface WebRole {

	/** 화면 역할의 Spring profile 이름 */
	String PROFILE = "web";

}
