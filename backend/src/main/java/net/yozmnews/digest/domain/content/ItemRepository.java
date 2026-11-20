package net.yozmnews.digest.domain.content;

import java.util.List;

import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;

/**
 * 항목 확인 저장소 — docs/specs/digest.md §4 · digest-R14.
 */
public interface ItemRepository {

	/**
	 * 그 사용자의 그 다이제스트에서 그 사건이 든 항목 섹션(주요 뉴스 · 헤드라인)이 하나면 그 섹션의 사건들을 위치 순으로 읽는다. 다이제스트의
	 * 상태 · 날짜를 보지 않는다 — 날짜별로 보이는 다이제스트에 덮인 다이제스트의 항목도 찾는다. 없는 다이제스트 · 다른 사용자의 다이제스트 · 그
	 * 사건이 든 섹션이 없거나 둘 이상이면 빈 목록이다.
	 */
	List<EventId> itemOf(UserId user, DigestId digest, EventId event);

}
