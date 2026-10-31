package net.yozmnews.admin.adapter.in.web;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 경로의 ID — 1 미만은 있을 수 없는 소스 · 피드라 404 다(식별자 생성자의 예외가 500 으로 새지 않게). docs/specs/admin.md §4 · admin-R2.
 */
final class AdminIds {

	private AdminIds() {
	}

	static SourceId source(long id) {
		if (id < 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 소스: " + id);
		}
		return new SourceId(id);
	}

	static FeedId feed(long id) {
		if (id < 1) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 피드: " + id);
		}
		return new FeedId(id);
	}

}
