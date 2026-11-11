package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.storyline.UnlinkedEvents;

/**
 * 기간의 소속 없는 사건 — 기간 안 기사가 있는데 어느 스토리라인에도 들지 않은 사건의 수와 그 기사 수 · docs/specs/admin.md §5 admin-R7.
 */
record UnlinkedEventsResponse(int events, int articles) {

	static UnlinkedEventsResponse of(UnlinkedEvents unlinked) {
		return new UnlinkedEventsResponse(unlinked.events(), unlinked.articles());
	}

}
