package net.yozmnews.platform;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 비밀 값 평문의 래퍼 — 문자열 표현이 값을 숨기고 직렬화하면 실패한다 · docs/specs/platform.md §3 · platform-R22.
 * <p>
 * {@code equals} · {@code hashCode} 를 재정의하지 않는다 — 같은 객체일 때만 같다. {@code get…} 꼴의 메서드를 두지 않는다 — 직렬화
 * 속성으로 잡힌다.
 */
public final class SecretValue {

	/** 문자열 표현 */
	private static final String MASKED = "SecretValue[숨김]";

	/** 직렬화하려 할 때의 예외 문구 */
	private static final String NOT_SERIALIZABLE = "SecretValue 는 직렬화하지 않는다";

	private final String value;

	private SecretValue(String value) {
		this.value = value;
	}

	/**
	 * @throws IllegalArgumentException {@code null} 이거나 비었을 때 — 문구에 값을 담지 않는다
	 */
	public static SecretValue of(String value) {
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException("비밀 값이 비었다");
		}
		return new SecretValue(value);
	}

	/** 평문 — 외부 호출에 싣는 자리에서만 부른다 */
	public String reveal() {
		return this.value;
	}

	/** 평문의 길이(문자 수) */
	public int length() {
		return this.value.length();
	}

	/** Jackson(2 · 3)이 이 값을 쓰려 하면 실패하게 한다 — 속성을 조용히 빼지 않는다 */
	@JsonValue
	private Object refuseSerialization() {
		throw new IllegalStateException(NOT_SERIALIZABLE);
	}

	@Override
	public String toString() {
		return MASKED;
	}

}
