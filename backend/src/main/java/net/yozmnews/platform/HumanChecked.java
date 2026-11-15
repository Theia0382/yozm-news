package net.yozmnews.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 봇 차단을 거는 화면용 API 처리기 메서드 — docs/specs/platform.md §3 · platform-R25.
 * <p>
 * 봇 차단을 켠 인스턴스에서 세션 체인이 처리기 앞(요청 제한 뒤)에서 요청 헤더의 사람 확인 토큰을 이 동작으로 검증한다. 같은 메서드에 같은 동작의
 * {@link RequestLimited} 가 있어야 한다 — 검증은 외부 요청이라 요청 제한에 걸린 요청은 검증하지 않는다. 메서드에만 단다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HumanChecked {

	/** 지키는 동작 — 봇 차단 위젯의 동작 이름은 {@link GuardedAction#actionName()} */
	GuardedAction value();

}
