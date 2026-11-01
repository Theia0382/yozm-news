package net.yozmnews.article.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.EntryRows;
import net.yozmnews.article.domain.EntryStatisticsRepository.EntryCountRow;
import net.yozmnews.article.domain.EntryWindow;
import net.yozmnews.source.FeedId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 엔트리 집계 질의 — 무엇을 중복 · 제목 판정 · 추정 · guid 재사용으로 세는지, 칸 경계를 Flyway 가 적용된 실제 PostgreSQL 에서 본다.
 * <p>
 * 기간은 2026-10-25T03:00Z 이상 2026-11-01T03:00Z 미만이고, 칸 i 는 시작 + i · 24시간부터다. 기대값은 손으로 셈한 수다.
 * <p>
 * 지키는 규칙: article-R13
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEntryStatisticsRepository.class })
class JdbcEntryStatisticsRepositoryTests {

	private static final EntryWindow WINDOW = EntryWindow.endingAt(Instant.parse("2026-11-01T03:00:00Z"));

	private static final long SOURCE = 1;

	@Autowired
	JdbcEntryStatisticsRepository repository;

	@Autowired
	JdbcClient jdbc;

	EntryRows rows;

	/** 시험 트랜잭션 안에서 지운다 — 다른 시험 클래스가 남긴 행이 결과에 섞이지 않게(시험이 끝나면 되돌린다) */
	@BeforeEach
	void deleteRows() {
		this.rows = new EntryRows(this.jdbc);
		this.rows.deleteAll();
	}

	@Test
	void noEntriesMeansNoRows() {
		assertThat(this.repository.countEntries(WINDOW)).isEmpty();
	}

	@Test
	void countsDuplicatesTitleMatchesAndEstimatesOfAFeedInABucket() {
		Instant at = Instant.parse("2026-10-31T12:00:00Z");
		entry(11, "NEW", true, null, at);
		entry(11, "URL", true, null, at);
		entry(11, "TITLE", false, null, at);
		entry(11, "URL", true, null, at);

		assertThat(this.repository.countEntries(WINDOW))
			.containsExactly(new EntryCountRow(new FeedId(11), 6, new EntryCounts(4, 3, 1, 3, 0)));
	}

	@Test
	void aFeedWhoseEntriesAllAttachedToEarlierArticlesIsAllDuplicate() {
		Instant at = Instant.parse("2026-10-27T00:00:00Z");
		entry(12, "URL", false, null, at);
		entry(12, "URL", false, null, at);
		entry(12, "TITLE", false, null, at);
		entry(12, "GUID", false, "g-known", at);

		assertThat(this.repository.countEntries(WINDOW))
			.containsExactly(new EntryCountRow(new FeedId(12), 1, new EntryCounts(4, 4, 1, 0, 0)));
	}

	@Test
	void aFeedWithoutReadableDatesIsAllEstimated() {
		Instant at = Instant.parse("2026-10-29T00:00:00Z");
		entry(13, "NEW", true, null, at);
		entry(13, "NEW", true, null, at);

		assertThat(this.repository.countEntries(WINDOW))
			.containsExactly(new EntryCountRow(new FeedId(13), 3, new EntryCounts(2, 0, 0, 2, 0)));
	}

	@Test
	void thePeriodIncludesItsStartAndExcludesItsEnd() {
		entry(14, "NEW", false, null, Instant.parse("2026-10-25T03:00:00Z"));
		entry(14, "NEW", false, null, Instant.parse("2026-10-26T02:59:59.999999Z"));
		entry(14, "NEW", false, null, Instant.parse("2026-10-26T03:00:00Z"));
		entry(14, "NEW", false, null, Instant.parse("2026-10-25T02:59:59Z"));
		entry(14, "NEW", false, null, Instant.parse("2026-11-01T03:00:00Z"));
		entry(14, "NEW", false, null, Instant.parse("2026-11-01T02:59:59.999999Z"));

		assertThat(this.repository.countEntries(WINDOW)).containsExactlyInAnyOrder(
				new EntryCountRow(new FeedId(14), 0, new EntryCounts(2, 0, 0, 0, 0)),
				new EntryCountRow(new FeedId(14), 1, new EntryCounts(1, 0, 0, 0, 0)),
				new EntryCountRow(new FeedId(14), 6, new EntryCounts(1, 0, 0, 0, 0)));
	}

	@Test
	void eachFeedIsCountedOnItsOwn() {
		Instant at = Instant.parse("2026-10-28T12:00:00Z");
		entry(15, "NEW", false, null, at);
		entry(16, "URL", true, null, at);

		assertThat(this.repository.countEntries(WINDOW)).containsExactlyInAnyOrder(
				new EntryCountRow(new FeedId(15), 3, new EntryCounts(1, 0, 0, 0, 0)),
				new EntryCountRow(new FeedId(16), 3, new EntryCounts(1, 1, 0, 1, 0)));
	}

	@Test
	void aGuidReusedForANewArticleIsCountedOnceOnTheLaterEntry() {
		// 처음 실린 기사는 세지 않고, 같은 guid 로 뒤에 새 기사가 된 엔트리만 센다
		entry(17, "NEW", false, "g-later", Instant.parse("2026-10-28T00:00:00Z"));
		entry(17, "NEW", false, "g-later", Instant.parse("2026-10-30T00:00:00Z"));
		// 한 수집에서 받은 둘은 받은 시각이 같다 — 먼저 넣은 기사(작은 ID)가 먼저다
		entry(17, "NEW", false, "g-same-poll", Instant.parse("2026-10-29T00:00:00Z"));
		entry(17, "NEW", false, "g-same-poll", Instant.parse("2026-10-29T00:00:00Z"));
		// 먼저 실린 기사가 기간 밖이어도 센다
		entry(17, "NEW", false, "g-before", Instant.parse("2026-10-24T00:00:00Z"));
		entry(17, "NEW", false, "g-before", Instant.parse("2026-10-27T00:00:00Z"));
		// 다른 피드의 같은 guid 는 재사용이 아니다
		entry(18, "NEW", false, "g-later", Instant.parse("2026-10-31T00:00:00Z"));

		assertThat(this.repository.countEntries(WINDOW)).containsExactlyInAnyOrder(
				new EntryCountRow(new FeedId(17), 1, new EntryCounts(1, 0, 0, 0, 1)),
				new EntryCountRow(new FeedId(17), 2, new EntryCounts(1, 0, 0, 0, 0)),
				new EntryCountRow(new FeedId(17), 3, new EntryCounts(2, 0, 0, 0, 1)),
				new EntryCountRow(new FeedId(17), 4, new EntryCounts(1, 0, 0, 0, 1)),
				new EntryCountRow(new FeedId(18), 5, new EntryCounts(1, 0, 0, 0, 0)));
	}

	@Test
	void aGuidOnAnEntryThatAttachedToAnExistingArticleIsNotAReuse() {
		entry(19, "NEW", false, "g-attached", Instant.parse("2026-10-28T00:00:00Z"));
		entry(19, "URL", false, "g-attached", Instant.parse("2026-10-30T00:00:00Z"));

		assertThat(this.repository.countEntries(WINDOW)).containsExactlyInAnyOrder(
				new EntryCountRow(new FeedId(19), 2, new EntryCounts(1, 0, 0, 0, 0)),
				new EntryCountRow(new FeedId(19), 4, new EntryCounts(1, 1, 0, 0, 0)));
	}

	/** 기사 하나와 그 기사의 엔트리 하나 — 기사는 넣은 순서로 ID 가 커진다 */
	private void entry(long feed, String matchKind, boolean estimated, @Nullable String guid, Instant seenAt) {
		this.rows.entry(this.rows.article(SOURCE), feed, matchKind, estimated, guid, seenAt);
	}

}
