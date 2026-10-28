package net.yozmnews.source.domain.registry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * 피드 저장소. 행은 {@link #insertIfAbsent} 로만 만든다 — 등록과 기동 대조가 같은 피드를 함께 만들려 해도 한 행이다.
 */
public interface FeedRepository extends CrudRepository<Feed, Long> {

	List<Feed> findAllBySourceId(long sourceId);

	Optional<Feed> findBySourceIdAndFeedKey(long sourceId, String feedKey);

	/**
	 * 꺼진 피드 행을 만든다. 같은 소스에 같은 피드 키가 이미 있으면 아무것도 하지 않는다.
	 * @return 만든 행 수(0 또는 1)
	 */
	@Modifying
	@Query("""
			insert into feeds (source_id, feed_key, enabled, poll_interval_minutes, version, created_at)
			values (:sourceId, :feedKey, false, null, 0, :createdAt)
			on conflict (source_id, feed_key) do nothing
			""")
	int insertIfAbsent(long sourceId, String feedKey, Instant createdAt);

}
