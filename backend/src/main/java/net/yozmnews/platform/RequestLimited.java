package net.yozmnews.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 제한을 거는 화면용 API 처리기 메서드 — docs/specs/platform.md §3 · platform-R25.
 * <p>
 * 세션 체인이 처리기 앞에서 이 동작의 한도로 요청을 센다. 메서드에만 단다 — 같은 클래스의 다른 처리기에 번지지 않게 한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestLimited {

	/** 지키는 동작 */
	GuardedAction value();

}
