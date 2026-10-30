package net.yozmnews.article.adapter.out.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.ArticleMatching;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.article.domain.IncomingItem;
import net.yozmnews.article.domain.KeyHash;
import net.yozmnews.article.domain.MatchCandidates;
import net.yozmnews.article.domain.MatchCandidates.GuidCandidate;
import net.yozmnews.article.domain.MatchCandidates.TitleCandidate;
import net.yozmnews.article.domain.MatchKind;
import net.yozmnews.article.domain.TimeRange;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ArticleRepository} 의 PostgreSQL 구현 — docs/specs/article.md §4
 */
@Repository
class JdbcArticleRepository implements ArticleRepository {

	/** advisory lock 의 첫 키 — 이 모듈의 판정 잠금을 다른 잠금과 가른다('ARTC') */
	static final int SOURCE_LOCK_NAMESPACE = 0x41525443;

	private final JdbcClient jdbc;

	JdbcArticleRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 둘째 키는 소스 ID 의 해시다 — 충돌하면 직렬화가 늘 뿐 판정은 틀리지 않는다 */
	@Override
	public void lockSource(SourceId sourceId) {
		this.jdbc.sql("select pg_advisory_xact_lock(:namespace, :key)")
			.param("namespace", SOURCE_LOCK_NAMESPACE)
			.param("key", Long.hashCode(sourceId.value()))
			.query()
			.singleRow();
	}

	@Override
	public MatchCandidates findCandidates(IncomingItem item) {
		KeyHash urlHash = item.canonicalUrl().hash();
		return new MatchCandidates(findByUrlHash(urlHash).orElse(null), findByEntryUrl(urlHash), findByGuid(item),
				findByTitle(item));
	}

	@Override
	public Optional<ArticleId> insertIfAbsent(IncomingItem item) {
		FetchedArticle article = item.article();
		URI thumbnail = article.thumbnail();
		return this.jdbc.sql("""
				insert into articles (source_id, canonical_url, url_hash, title, title_key_hash, summary_text, author,
				    published_at, published_at_estimated, image_url, first_seen_at)
				values (:sourceId, :canonicalUrl, :urlHash, :title, :titleKeyHash, :summary, :author,
				    :publishedAt, :publishedAtEstimated, :imageUrl, :firstSeenAt)
				on conflict (url_hash) do nothing
				returning id
				""")
			.param("sourceId", item.sourceId().value())
			.param("canonicalUrl", item.canonicalUrl().value())
			.param("urlHash", item.canonicalUrl().hash().bytes())
			.param("title", article.title())
			.param("titleKeyHash", item.titleKey().hash().bytes())
			.param("summary", article.summary(), Types.VARCHAR)
			.param("author", article.author(), Types.VARCHAR)
			.param("publishedAt", timestamp(article.publishedAt()))
			.param("publishedAtEstimated", article.publishedAtEstimated())
			.param("imageUrl", (thumbnail != null) ? thumbnail.toString() : null, Types.VARCHAR)
			.param("firstSeenAt", timestamp(item.fetchedAt()))
			.query(Long.class)
			.optional()
			.map(ArticleId::new);
	}

	@Override
	public Optional<ArticleId> findByUrlHash(KeyHash urlHash) {
		return this.jdbc.sql("select id from articles where url_hash = :hash")
			.param("hash", urlHash.bytes())
			.query(Long.class)
			.optional()
			.map(ArticleId::new);
	}

	@Override
	public boolean insertEntryIfAbsent(ArticleId articleId, IncomingItem item, MatchKind kind) {
		FetchedArticle article = item.article();
		KeyHash guidHash = item.guidHash();
		int inserted = this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, item_url_hash, guid, guid_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				values (:articleId, :feedId, :itemUrlHash, :guid, :guidHash, :categories,
				    :publishedAtEstimated, :matchKind, :seenAt)
				on conflict (article_id, feed_id) do nothing
				""")
			.param("articleId", articleId.value())
			.param("feedId", item.feedId().value())
			.param("itemUrlHash", item.canonicalUrl().hash().bytes())
			.param("guid", article.guid(), Types.VARCHAR)
			.param("guidHash", (guidHash != null) ? guidHash.bytes() : null, Types.BINARY)
			.param("categories", article.categories().toArray(String[]::new))
			.param("publishedAtEstimated", article.publishedAtEstimated())
			.param("matchKind", kind.name())
			.param("seenAt", timestamp(item.fetchedAt()))
			.update();
		return inserted == 1;
	}

	private @Nullable ArticleId findByEntryUrl(KeyHash urlHash) {
		return this.jdbc.sql("""
				select article_id from article_feed_entries where item_url_hash = :hash
				order by article_id limit 1
				""").param("hash", urlHash.bytes()).query(Long.class).optional().map(ArticleId::new).orElse(null);
	}

	private List<GuidCandidate> findByGuid(IncomingItem item) {
		KeyHash guidHash = item.guidHash();
		if (guidHash == null) {
			return List.of();
		}
		return this.jdbc.sql("""
				select e.article_id, a.title_key_hash from article_feed_entries e
				join articles a on a.id = e.article_id
				where e.feed_id = :feedId and e.guid_hash = :guidHash
				""")
			.param("feedId", item.feedId().value())
			.param("guidHash", guidHash.bytes())
			.query((rs, rowNum) -> new GuidCandidate(new ArticleId(rs.getLong("article_id")),
					KeyHash.fromBytes(rs.getBytes("title_key_hash"))))
			.list();
	}

	private List<TitleCandidate> findByTitle(IncomingItem item) {
		if (item.titleKey().isEmpty()) {
			return List.of();
		}
		TimeRange window = ArticleMatching.titleWindowAround(item.article().publishedAt());
		return this.jdbc.sql("""
				select a.id, a.published_at,
				    exists (select 1 from article_feed_entries e where e.article_id = a.id and e.feed_id = :feedId)
				        as seen_in_feed
				from articles a
				where a.source_id = :sourceId and a.title_key_hash = :titleKeyHash
				    and a.published_at between :from and :to
				""")
			.param("feedId", item.feedId().value())
			.param("sourceId", item.sourceId().value())
			.param("titleKeyHash", item.titleKey().hash().bytes())
			.param("from", timestamp(window.from()))
			.param("to", timestamp(window.to()))
			.query((rs, rowNum) -> new TitleCandidate(new ArticleId(rs.getLong("id")), instant(rs, "published_at"),
					rs.getBoolean("seen_in_feed")))
			.list();
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
