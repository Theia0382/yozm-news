package net.yozmnews.source.application;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceDirectory;
import net.yozmnews.source.domain.registry.FeedRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 조회 {@link SourceDirectory} 의 구현 — docs/specs/source.md §4. 트랜잭션을 열지 않고 호출자의 트랜잭션에서 읽는다.
 */
@Service
class SourceDirectoryService implements SourceDirectory {

	private final FeedRepository feeds;

	SourceDirectoryService(FeedRepository feeds) {
		this.feeds = feeds;
	}

	@Override
	public boolean feedExists(FeedId id) {
		return this.feeds.existsById(id.value());
	}

}
