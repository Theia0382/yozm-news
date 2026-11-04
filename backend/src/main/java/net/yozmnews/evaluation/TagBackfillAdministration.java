package net.yozmnews.evaluation;

import java.util.List;

/**
 * 빠진 태그 채움의 운영 — 저장 기준을 낮추기 전에 평가된 평가의 원본 응답을 다시 읽어 지금 저장 기준 이상인데 없는 태그 행을 더한다 ·
 * docs/specs/evaluation.md §3.
 * <p>
 * {@code admin} 이 운영자 요청으로 부른다. 실행은 작업 역할의 반복 작업이 한다 — 요청은 채움 하나를 기록할 뿐이다.
 */
public interface TagBackfillAdministration {

	/** 채울 기간의 상한(일) — 정본이다 */
	int MAX_DAYS = 180;

	/** 확인 — 지금부터 그 일수 안에 평가된 평가 수를 버전별로 센다. 아무것도 쓰지 않는다 */
	TagBackfillReviewResult review(int days);

	/** 요청 — 채움 하나를 기록한다. 끝나지 않은 채움이 있으면 그것을 돌려주고 아무것도 쓰지 않는다 */
	TagBackfillRequestResult request(int days);

	/** 채움 목록 — 최근 것부터 */
	List<TagBackfillRunView> runs();

}
