package net.yozmnews.feedback;

import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;

/**
 * 다이제스트의 항목 확인 — 이 모듈이 선언하고 {@code digest} 가 구현한다. docs/specs/feedback.md §3 · feedback-R2.
 * <p>
 * 이 모듈은 {@code digest} 의 타입을 쓰지 않고 다이제스트를 ID 로만 든다. 부르는 쪽의 트랜잭션에 들고 쓰지 않는다.
 */
public interface DigestItems {

	/**
	 * 그 다이제스트가 그 사용자의 것이고 그 사건이 든 항목(주요 뉴스 · 헤드라인 섹션)이 하나면 그 항목의 사건들을 위치 순으로 낸다 — 첫 사건이
	 * 항목 키다. 없는 다이제스트 · 다른 사용자의 다이제스트 · 그 다이제스트가 보이지 않은 사건 · 그 사건이 든 항목이 둘 이상이면 빈 목록이다.
	 * 날짜별로 보이는 다이제스트인지는 보지 않는다.
	 */
	List<EventId> itemOf(UserId user, long digestId, EventId event);

}
