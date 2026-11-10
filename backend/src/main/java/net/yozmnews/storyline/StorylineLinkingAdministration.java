package net.yozmnews.storyline;

/**
 * 스토리라인 잇기 채우기의 운영 — 운영자가 정한 기간에 마지막 기사가 있는 사건을 잇기 대기에 넣는다 · docs/specs/storyline.md §3 ·
 * storyline-R15.
 * <p>
 * {@code admin} 이 운영자 요청으로 부른다. 넣은 사건은 작업 역할의 반복 작업이 판정한다 — 이 기능을 켜기 전에 있던 사건을 잇기에 들이는
 * 길이다.
 */
public interface StorylineLinkingAdministration {

	/** 채울 기간의 상한(일) — 정본이다 */
	int MAX_DAYS = 180;

	/**
	 * 마지막 기사 시각이 지금 − 일수 × 24시간 이후(그 시각 포함)인, 합쳐지지 않은 사건을 잇기 대기에 넣는다 — 이미 대기에 있으면 다시
	 * 알림으로 센다
	 */
	LinkingFillResult fill(int days);

}
