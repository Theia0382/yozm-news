package net.yozmnews.article.adapter.out.persistence;

import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.domain.EntryStatisticsRepository;
import net.yozmnews.article.domain.EntryWindow;
import net.yozmnews.source.FeedId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link EntryStatisticsRepository} 의 PostgreSQL 구현 — docs/specs/article.md §4.
 * <p>
 * 무엇을 중복 · URL 판정 · 제목 판정 · 추정 · guid 재사용으로 세는지의 정본은 이 질의다(article-R13). guid 재사용은 같은 피드 · 같은 guid 의
 * 엔트리가 다른 기사에 먼저 있는 새 기사 엔트리다 — 먼저는 (받은 시각, 기사 ID) 순이다. 한 수집에서 받은 둘은 받은 시각이 같고, 같은
 * 소스의 판정은 소스 단위 잠금으로 줄을 서 기사 ID 가 넣은 순서로 커진다(article-R9). 비교 대상은 기간 밖이어도 된다.
 */
@Repository
class JdbcEntryStatisticsRepository implements EntryStatisticsRepository {

	private final JdbcClient jdbc;

	JdbcEntryStatisticsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<EntryCountRow> countEntries(EntryWindow window) {
		return this.jdbc.sql("""
				select e.feed_id,
				       floor(extract(epoch from (e.seen_at - :from)) / :bucketSeconds)::int as bucket,
				       count(*) as entries,
				       count(*) filter (where e.match_kind <> 'NEW') as duplicates,
				       count(*) filter (where e.match_kind = 'URL') as url_matches,
				       count(*) filter (where e.match_kind = 'TITLE') as title_matches,
				       count(*) filter (where e.published_at_estimated) as estimated,
				       count(*) filter (where e.match_kind = 'NEW' and e.guid_hash is not null and exists (
				           select 1 from article_feed_entries p
				           where p.feed_id = e.feed_id and p.guid_hash = e.guid_hash and p.article_id <> e.article_id
				             and (p.seen_at, p.article_id) < (e.seen_at, e.article_id))) as guid_reuses
				from article_feed_entries e
				where e.seen_at >= :from and e.seen_at < :to
				group by e.feed_id, bucket
				""")
			.param("from", window.from().atOffset(ZoneOffset.UTC))
			.param("to", window.to().atOffset(ZoneOffset.UTC))
			.param("bucketSeconds", EntryWindow.BUCKET.toSeconds())
			.query((rs, row) -> new EntryCountRow(new FeedId(rs.getLong("feed_id")), rs.getInt("bucket"),
					new EntryCounts(rs.getInt("entries"), rs.getInt("duplicates"), rs.getInt("url_matches"),
							rs.getInt("title_matches"), rs.getInt("estimated"), rs.getInt("guid_reuses"))))
			.list();
	}

}
