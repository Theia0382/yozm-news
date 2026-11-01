package net.yozmnews.platform.domain;

import java.util.function.Supplier;

/**
 * 이벤트를 읽을 수 있는지의 판정 정본 — docs/specs/platform.md platform-R16.
 * <p>
 * 읽기가 던진 예외는 로그에 남기지 않는다 — 역직렬화 예외의 문구가 이벤트 내용 일부를 담을 수 있다.
 */
public final class EventReading {

	private EventReading() {
	}

	/**
	 * 읽기가 값을 돌려주면 참, 예외를 던지거나 값이 없으면 거짓이다. 클래스 연결 오류(필드 타입의 클래스가 없음 등)도 거짓이다.
	 * @param read 이벤트를 읽는다
	 * @return 읽을 수 있는지
	 */
	public static boolean readable(Supplier<?> read) {
		try {
			return read.get() != null;
		}
		catch (RuntimeException | LinkageError ex) {
			return false;
		}
	}

}
