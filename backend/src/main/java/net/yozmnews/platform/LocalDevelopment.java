package net.yozmnews.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Profile;

/**
 * 로컬 개발(profile {@value #PROFILE})에서만 켜는 구성요소 — 로컬 대체물의 결선에 단다 · docs/specs/platform.md §3 · platform-R18
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile(LocalDevelopment.PROFILE)
public @interface LocalDevelopment {

	/** 로컬 개발의 Spring profile 이름 */
	String PROFILE = "local";

}
