package net.yozmnews.platform;

/**
 * 재처리 시험용 이벤트. {@link ResubmissionProbeListener} 가 {@code behavior} 대로 처리하고 {@code id} 별 호출 수를 센다.
 */
public record ResubmissionProbeEvent(String id, Behavior behavior) {

	public enum Behavior {

		SUCCEED, FAIL_ONCE, ALWAYS_FAIL

	}

}
