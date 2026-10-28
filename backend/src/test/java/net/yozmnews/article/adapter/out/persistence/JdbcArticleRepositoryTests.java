package net.yozmnews.article.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.domain.IncomingItem;
import net.yozmnews.article.domain.MatchCandidates;
import net.yozmnews.article.domain.MatchCandidates.TitleCandidate;
import net.yozmnews.article.domain.MatchKind;
import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 저장 · 고유 제약 · 후보 조회를 Flyway 가 적용된 실제 PostgreSQL 에서. 테스트마다 트랜잭션을 롤백한다. 해시 기대값은
 * {@code sha256sum} 으로 따로 계산한 16진수다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcArticleRepository.class })
class JdbcArticleRepositoryTests {

	private static final SourceId SOURCE = new SourceId(3);

	private static final FeedId FEED = new FeedId(5);

	private static final FeedId OTHER_FEED = new FeedId(6);

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-28T00:00:00Z");

	private static final Instant FETCHED_AT = Instant.parse("2026-10-28T01:00:00Z");

	private static final String TITLE = "국회 본회의 통과";

	@Autowired
	JdbcArticleRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void locksTheSourceUnderTheModuleNamespace() {
		// 둘째 키는 소스 ID 의 Long.hashCode — 4294967298(0x1_0000_0002)은 위 · 아래 32비트의 XOR 인 3 이 된다
		this.repository.lockSource(new SourceId(4_294_967_298L));

		assertThat(this.jdbc.sql("""
				select classid::bigint as namespace, objid::bigint as key, objsubid::int as objsubid from pg_locks
				where locktype = 'advisory' and pid = pg_backend_pid()
				""").query().singleRow()).containsEntry("namespace", 1_095_914_563L)
			.containsEntry("key", 3L)
			.containsEntry("objsubid", 2);
	}

	@Test
	void storesTheArticleAndItsFirstEntry() {
		IncomingItem item = IncomingItem.from(new ArticleFetched(SOURCE, FEED, FETCHED_AT,
				new FetchedArticle(TITLE, URI.create("https://A.example/news/1?utm_source=rss"), "g-1", "요약",
						PUBLISHED_AT, true, "홍길동", List.of("정치", "국회"), URI.create("http://a.example/t.jpg"))));

		ArticleId id = this.repository.insertIfAbsent(item).orElseThrow();
		boolean inserted = this.repository.insertEntryIfAbsent(id, item, MatchKind.NEW);

		assertThat(inserted).isTrue();
		Map<String, Object> article = this.jdbc.sql("""
				select source_id, canonical_url, encode(url_hash, 'hex') as url_hash, title,
				    encode(title_key_hash, 'hex') as title_key_hash, summary_text, author, published_at,
				    published_at_estimated, image_url, first_seen_at
				from articles where id = :id
				""").param("id", id.value()).query().singleRow();
		assertThat(article).containsEntry("source_id", 3L)
			.containsEntry("canonical_url", "https://a.example/news/1")
			.containsEntry("url_hash", "0255a70a6060ad42610393d88c1de21f1f51cf6e6540fba7e8a5b1df21bd487f")
			.containsEntry("title", "국회 본회의 통과")
			.containsEntry("title_key_hash", "a681129ed8417ef41f715b6f89634823bc1c4934b3e82f0c0ff71ad0b09fd70f")
			.containsEntry("summary_text", "요약")
			.containsEntry("author", "홍길동")
			.containsEntry("published_at_estimated", true)
			.containsEntry("image_url", "http://a.example/t.jpg");
		assertThat(instant(article.get("published_at"))).isEqualTo(Instant.parse("2026-10-28T00:00:00Z"));
		assertThat(instant(article.get("first_seen_at"))).isEqualTo(Instant.parse("2026-10-28T01:00:00Z"));

		Map<String, Object> entry = entryRow(id, FEED);
		assertThat(entry).containsEntry("item_url_hash",
				"0255a70a6060ad42610393d88c1de21f1f51cf6e6540fba7e8a5b1df21bd487f")
			.containsEntry("guid", "g-1")
			.containsEntry("guid_hash", "b9817549cc546b0ee549a42a97b94811ed862005f8c17409217cc58128d8f630")
			.containsEntry("feed_categories", "{정치,국회}")
			.containsEntry("published_at_estimated", true)
			.containsEntry("match_kind", "NEW");
		assertThat(instant(entry.get("seen_at"))).isEqualTo(Instant.parse("2026-10-28T01:00:00Z"));
	}

	@Test
	void storesMissingOptionalValuesAsNull() {
		IncomingItem item = item(SOURCE, FEED, "https://a.example/news/2", null, TITLE, PUBLISHED_AT, FETCHED_AT,
				List.of());

		ArticleId id = this.repository.insertIfAbsent(item).orElseThrow();
		this.repository.insertEntryIfAbsent(id, item, MatchKind.NEW);

		Map<String, Object> article = this.jdbc
			.sql("select summary_text, author, image_url, published_at_estimated from articles where id = :id")
			.param("id", id.value())
			.query()
			.singleRow();
		assertThat(article).containsEntry("summary_text", null)
			.containsEntry("author", null)
			.containsEntry("image_url", null)
			.containsEntry("published_at_estimated", false);
		Map<String, Object> entry = entryRow(id, FEED);
		assertThat(entry).containsEntry("guid", null)
			.containsEntry("guid_hash", null)
			.containsEntry("feed_categories", "{}")
			.containsEntry("published_at_estimated", false);
	}

	@Test
	void keepsTheFirstEntryOfAnArticleInAFeed() {
		IncomingItem first = item(SOURCE, FEED, "https://a.example/news/1", "g-1", TITLE, PUBLISHED_AT, FETCHED_AT,
				List.of("정치"));
		IncomingItem later = item(SOURCE, FEED, "https://a.example/news/1?ref=x", "g-2", TITLE, PUBLISHED_AT,
				Instant.parse("2026-10-28T02:00:00Z"), List.of("사회"));
		ArticleId id = this.repository.insertIfAbsent(first).orElseThrow();
		this.repository.insertEntryIfAbsent(id, first, MatchKind.NEW);

		boolean inserted = this.repository.insertEntryIfAbsent(id, later, MatchKind.URL);

		assertThat(inserted).isFalse();
		assertThat(entryCount()).isEqualTo(1);
		assertThat(entryRow(id, FEED)).containsEntry("guid", "g-1")
			.containsEntry("feed_categories", "{정치}")
			.containsEntry("match_kind", "NEW");
		assertThat(instant(entryRow(id, FEED).get("seen_at"))).isEqualTo(Instant.parse("2026-10-28T01:00:00Z"));
	}

	@Test
	void doesNotInsertASecondArticleWithTheSameCanonicalUrl() {
		ArticleId first = this.repository
			.insertIfAbsent(item(SOURCE, FEED, "https://a.example/news/1", null, TITLE, PUBLISHED_AT, FETCHED_AT,
					List.of()))
			.orElseThrow();
		IncomingItem sameUrl = item(new SourceId(4), OTHER_FEED, "HTTPS://A.EXAMPLE/news/1#c", null, "다른 제목",
				PUBLISHED_AT, FETCHED_AT, List.of());

		assertThat(this.repository.insertIfAbsent(sameUrl)).isEmpty();
		assertThat(this.repository.findByUrlHash(sameUrl.canonicalUrl().hash())).contains(first);
		assertThat(this.jdbc.sql("select count(*) from articles").query(Long.class).single()).isEqualTo(1L);
	}

	@Test
	void everyMatchKindPassesTheColumnCheck() {
		IncomingItem item = item(SOURCE, FEED, "https://a.example/news/1", null, TITLE, PUBLISHED_AT, FETCHED_AT,
				List.of());
		ArticleId id = this.repository.insertIfAbsent(item).orElseThrow();
		long feed = 100;
		for (MatchKind kind : MatchKind.values()) {
			IncomingItem inFeed = item(SOURCE, new FeedId(feed++), "https://a.example/news/1", null, TITLE,
					PUBLISHED_AT, FETCHED_AT, List.of());
			assertThat(this.repository.insertEntryIfAbsent(id, inFeed, kind)).as(kind.name()).isTrue();
		}
		assertThat(this.jdbc.sql("select match_kind from article_feed_entries order by feed_id")
			.query(String.class)
			.list()).containsExactly("NEW", "URL", "GUID", "TITLE");
	}

	@Test
	void findsArticleAndEntryUrlCandidates() {
		ArticleId article = store(item(SOURCE, FEED, "https://a.example/news/1", null, TITLE, PUBLISHED_AT,
				FETCHED_AT, List.of()), MatchKind.NEW);
		ArticleId lower = store(item(SOURCE, FEED, "https://a.example/news/2", null, "둘", PUBLISHED_AT, FETCHED_AT,
				List.of()), MatchKind.NEW);
		ArticleId higher = store(item(SOURCE, FEED, "https://a.example/news/3", null, "셋", PUBLISHED_AT, FETCHED_AT,
				List.of()), MatchKind.NEW);
		// 같은 항목 URL(news/9)이 두 기사의 엔트리에 남아 있다 — 다른 피드에서 L2 로 붙은 경우
		IncomingItem alias = item(SOURCE, OTHER_FEED, "https://a.example/news/9", null, "둘", PUBLISHED_AT,
				FETCHED_AT, List.of());
		this.repository.insertEntryIfAbsent(higher, alias, MatchKind.TITLE);
		this.repository.insertEntryIfAbsent(lower, alias, MatchKind.TITLE);

		MatchCandidates byArticle = this.repository.findCandidates(item(new SourceId(9), new FeedId(9),
				"https://A.example/news/1", null, "무관", PUBLISHED_AT, FETCHED_AT, List.of()));
		MatchCandidates byEntry = this.repository.findCandidates(item(new SourceId(9), new FeedId(9),
				"https://a.example/news/9?utm_medium=x", null, "무관", PUBLISHED_AT, FETCHED_AT, List.of()));

		assertThat(byArticle.byArticleUrl()).isEqualTo(article);
		assertThat(byArticle.byEntryUrl()).isEqualTo(article);
		assertThat(byEntry.byArticleUrl()).isNull();
		assertThat(byEntry.byEntryUrl()).isEqualTo(lower);
	}

	@Test
	void findsGuidCandidatesInTheSameFeedOnly() {
		ArticleId inFeed = store(item(SOURCE, FEED, "https://a.example/news/1", "g-1", TITLE, PUBLISHED_AT,
				FETCHED_AT, List.of()), MatchKind.NEW);
		store(item(SOURCE, OTHER_FEED, "https://a.example/news/2", "g-1", TITLE, PUBLISHED_AT, FETCHED_AT,
				List.of()), MatchKind.NEW);

		MatchCandidates withGuid = this.repository.findCandidates(item(SOURCE, FEED, "https://a.example/news/3",
				"g-1", "다른 제목", PUBLISHED_AT, FETCHED_AT, List.of()));
		MatchCandidates withoutGuid = this.repository.findCandidates(item(SOURCE, FEED, "https://a.example/news/3",
				null, "다른 제목", PUBLISHED_AT, FETCHED_AT, List.of()));

		assertThat(withGuid.byGuid()).singleElement().satisfies((candidate) -> {
			assertThat(candidate.articleId()).isEqualTo(inFeed);
			assertThat(candidate.titleKeyHash())
				.hasToString("a681129ed8417ef41f715b6f89634823bc1c4934b3e82f0c0ff71ad0b09fd70f");
		});
		assertThat(withoutGuid.byGuid()).isEmpty();
	}

	@Test
	void findsTitleCandidatesWithinTheWindowOfTheSameSource() {
		ArticleId atBoundary = store(item(SOURCE, OTHER_FEED, "https://a.example/news/1", null, TITLE,
				Instant.parse("2026-10-26T00:00:00Z"), FETCHED_AT, List.of()), MatchKind.NEW);
		ArticleId atUpperBoundary = store(item(SOURCE, OTHER_FEED, "https://a.example/news/5", null, TITLE,
				Instant.parse("2026-10-30T00:00:00Z"), FETCHED_AT, List.of()), MatchKind.NEW);
		store(item(SOURCE, OTHER_FEED, "https://a.example/news/2", null, TITLE, Instant.parse("2026-10-30T00:00:01Z"),
				FETCHED_AT, List.of()), MatchKind.NEW);
		store(item(SOURCE, OTHER_FEED, "https://a.example/news/6", null, TITLE, Instant.parse("2026-10-25T23:59:59Z"),
				FETCHED_AT, List.of()), MatchKind.NEW);
		ArticleId seenHere = store(item(SOURCE, FEED, "https://a.example/news/3", null, TITLE,
				Instant.parse("2026-10-28T03:00:00Z"), FETCHED_AT, List.of()), MatchKind.NEW);
		store(item(new SourceId(4), OTHER_FEED, "https://b.example/news/1", null, TITLE, PUBLISHED_AT, FETCHED_AT,
				List.of()), MatchKind.NEW);
		store(item(SOURCE, OTHER_FEED, "https://a.example/news/4", null, "국회 본회의 부결", PUBLISHED_AT, FETCHED_AT,
				List.of()), MatchKind.NEW);

		MatchCandidates candidates = this.repository.findCandidates(item(SOURCE, FEED, "https://a.example/news/9",
				null, "국회  본회의 통과", PUBLISHED_AT, FETCHED_AT, List.of()));

		assertThat(candidates.byTitle()).containsExactlyInAnyOrder(
				new TitleCandidate(atBoundary, Instant.parse("2026-10-26T00:00:00Z"), false),
				new TitleCandidate(atUpperBoundary, Instant.parse("2026-10-30T00:00:00Z"), false),
				new TitleCandidate(seenHere, Instant.parse("2026-10-28T03:00:00Z"), true));
	}

	@Test
	void doesNotLookUpTitleCandidatesForAnEmptyKey() {
		store(item(SOURCE, OTHER_FEED, "https://a.example/news/1", null, "\u00A0", PUBLISHED_AT, FETCHED_AT,
				List.of()), MatchKind.NEW);

		MatchCandidates candidates = this.repository.findCandidates(item(SOURCE, FEED, "https://a.example/news/2",
				null, "\u00A0\u00A0", PUBLISHED_AT, FETCHED_AT, List.of()));

		assertThat(candidates.byTitle()).isEmpty();
	}

	@Test
	void storesAndFindsValuesBeyondTheIndexEntryLimit() {
		String longText = distinctSyllables(10_000);
		IncomingItem item = item(SOURCE, FEED, "https://a.example/" + longText, longText, longText, PUBLISHED_AT,
				FETCHED_AT, List.of());
		ArticleId id = store(item, MatchKind.NEW);

		MatchCandidates sameUrl = this.repository.findCandidates(item);
		MatchCandidates sameGuidAndTitle = this.repository.findCandidates(item(SOURCE, FEED, "https://a.example/x",
				longText, longText, PUBLISHED_AT, FETCHED_AT, List.of()));
		MatchCandidates sameTitle = this.repository.findCandidates(item(SOURCE, OTHER_FEED, "https://a.example/y",
				null, longText, PUBLISHED_AT, FETCHED_AT, List.of()));

		assertThat(sameUrl.byArticleUrl()).isEqualTo(id);
		assertThat(sameGuidAndTitle.byGuid()).extracting(MatchCandidates.GuidCandidate::articleId).containsExactly(id);
		assertThat(sameTitle.byTitle()).extracting(TitleCandidate::articleId).containsExactly(id);
		assertThat(this.jdbc.sql("select length(title) from articles where id = :id")
			.param("id", id.value())
			.query(Integer.class)
			.single()).isEqualTo(10_000);
	}

	private ArticleId store(IncomingItem item, MatchKind kind) {
		ArticleId id = this.repository.insertIfAbsent(item).orElseThrow();
		this.repository.insertEntryIfAbsent(id, item, kind);
		return id;
	}

	private Map<String, Object> entryRow(ArticleId articleId, FeedId feedId) {
		return this.jdbc.sql("""
				select encode(item_url_hash, 'hex') as item_url_hash, guid, encode(guid_hash, 'hex') as guid_hash,
				    feed_categories::text as feed_categories, published_at_estimated, match_kind, seen_at
				from article_feed_entries where article_id = :articleId and feed_id = :feedId
				""").param("articleId", articleId.value()).param("feedId", feedId.value()).query().singleRow();
	}

	private long entryCount() {
		return this.jdbc.sql("select count(*) from article_feed_entries").query(Long.class).single();
	}

	private static Instant instant(@Nullable Object timestamp) {
		return switch (timestamp) {
			case OffsetDateTime offset -> offset.toInstant();
			case java.sql.Timestamp sql -> sql.toInstant();
			case null, default -> throw new IllegalStateException("시각이 아니다: " + timestamp);
		};
	}

	/** U+AC00 부터 연속한 서로 다른 한글 음절 — 압축되지 않는 긴 값 */
	private static String distinctSyllables(int count) {
		return IntStream.range(0, count)
			.map((i) -> 0xAC00 + i)
			.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
			.toString();
	}

	/** 요약문 · 기자명 · 썸네일이 없고 발행 시각을 해석한 항목 */
	private static IncomingItem item(SourceId source, FeedId feed, String link, @Nullable String guid, String title,
			Instant publishedAt, Instant fetchedAt, List<String> categories) {
		return IncomingItem.from(new ArticleFetched(source, feed, fetchedAt, new FetchedArticle(title, URI.create(link),
				guid, null, publishedAt, false, null, categories, null)));
	}

}
