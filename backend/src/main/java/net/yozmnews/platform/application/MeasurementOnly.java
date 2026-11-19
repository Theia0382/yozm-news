package net.yozmnews.platform.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Profile;

/**
 * 측정 profile({@value #PROFILE})에서만 켜는 구성요소 — 측정용 시계의 결선에 단다 · docs/specs/platform.md §4
 * <p>
 * profile 이름의 코드 쪽 정본은 이 상수다. 다른 {@code @Profile} 이 달린 구성요소에 함께 달면 둘 가운데 하나만 맞아도 등록되므로 그런 자리에는
 * 쓰지 않는다.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Profile(MeasurementOnly.PROFILE)
public @interface MeasurementOnly {

	/** 측정 profile 이름 */
	String PROFILE = "measurement";

}
