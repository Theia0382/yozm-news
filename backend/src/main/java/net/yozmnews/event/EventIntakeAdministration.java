package net.yozmnews.event;

/**
 * 사건 묶기 채우기의 운영 — 운영자가 정한 기간에 처음 본 기사 가운데 사건 배정이 없는 기사를 사건 묶기 대기에 넣는다 ·
 * docs/specs/event.md §3.
 * <p>
 * {@code admin} 이 운영자 요청으로 부른다. 넣은 기사는 작업 역할의 반복 작업이 표현을 만든 뒤 배정한다 — 이 모듈이 생기기 전에 저장된 기사,
 * 사건 묶기를 켜기 전의 기사를 배정에 들이는 길이다.
 */
public interface EventIntakeAdministration {

	/** 채울 기간의 상한(일) — 정본이다 */
	int MAX_DAYS = 30;

	/** 처음 본 시각이 지금 − 일수 × 24시간 이후이고 배정이 없는 기사를 사건 묶기 대기에 넣는다 — 이미 대기에 있으면 그대로 */
	IntakeFillResult fill(int days);

}
