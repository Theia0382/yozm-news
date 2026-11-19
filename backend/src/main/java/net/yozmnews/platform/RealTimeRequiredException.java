package net.yozmnews.platform;

/**
 * 앱의 시각이 실제 시간이 아니라 실제 시간의 외부 요청을 하지 않는다 — 측정용 시계에서 실제 피드를 요청하는 운영자 길이 거부된다 ·
 * docs/specs/platform.md §3
 * <p>
 * 화면용 API 처리기에서 나오면 409 문제 응답이 된다.
 */
public class RealTimeRequiredException extends RuntimeException {

	/** 문제 응답의 detail 이기도 한 메시지 */
	public static final String MESSAGE = "측정용 시계에서는 실제 피드를 요청하지 않는다 — 이 인스턴스의 시계는 실제 시간이 아니다";

	public RealTimeRequiredException() {
		super(MESSAGE);
	}

}
