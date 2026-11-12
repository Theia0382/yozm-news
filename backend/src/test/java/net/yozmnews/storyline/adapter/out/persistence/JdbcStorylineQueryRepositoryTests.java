package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.domain.storyline.StoredStoryline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 스토리라인 조회 저장소 — 합쳐진 스토리라인은 남는 스토리라인으로 찾는지, 주어진 사건이 든 합쳐지지 않은 스토리라인만 고르는지, 쪽이 마지막 기사
 * 시각 · ID 내림차순과 커서를 따르는지, 구성 사건 수가 소속 행 수인지를 실제 PostgreSQL 에서 본다. 사건은 ID 로만 가리켜 사건 행을 넣지 않는다.
 * <p>
 * 지키는 규칙: storyline-R18 · storyline-R21 · storyline-R22
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcStorylineQueryRepository.class })
class JdbcStorylineQueryRepositoryTests {

	@Autowired
	JdbcStorylineQueryRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void aMergedStorylineIsFoundAsItsSurvivorAndAMissingOneIsEmpty() {
		long survivor = storyline(7, "2026-11-01T00:00:00Z", "2026-11-05T00:00:00Z", 880_001);
		member(survivor, 880_001);
		member(survivor, 880_002);
		long merged = storyline(2, "2026-11-02T00:00:00Z", "2026-11-02T00:00:00Z", 880_002);
		merge(merged, survivor);

		StoredStoryline found = this.repository.find(survivor).orElseThrow();
		assertThat(found.id()).isEqualTo(survivor);
		assertThat(found.eventCount()).isEqualTo(2);
		assertThat(found.articleCount()).isEqualTo(7);
		assertThat(found.firstArticleAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(found.lastArticleAt()).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
		assertThat(found.representativeEventId()).isEqualTo(880_001L);
		assertThat(this.repository.find(merged)).contains(found);
		assertThat(this.repository.find(999_999_999L)).isEmpty();
	}

	@Test
	void storylinesHavingAnyOfTheEventsAreUnmergedAndInIdOrder() {
		long first = storyline(3, "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z", 881_001);
		member(first, 881_001);
		member(first, 881_002);
		long second = storyline(3, "2026-11-01T00:00:00Z", "2026-11-04T00:00:00Z", 881_002);
		member(second, 881_002);
		member(second, 881_003);
		long other = storyline(3, "2026-11-01T00:00:00Z", "2026-11-05T00:00:00Z", 881_004);
		member(other, 881_004);
		// 합쳐진 스토리라인은 소속이 없다 — 소속이 남아 있어도 고르지 않는다
		long merged = storyline(3, "2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z", 881_001);
		merge(merged, first);
		member(merged, 881_001);

		assertThat(this.repository.having(List.of(881_002L))).extracting(StoredStoryline::id).containsExactly(first, second);
		assertThat(this.repository.having(List.of(881_003L, 881_001L))).extracting(StoredStoryline::id)
			.containsExactly(first, second);
		assertThat(this.repository.having(List.of(881_099L))).isEmpty();
		assertThat(this.repository.having(List.of())).isEmpty();
	}

	@Test
	void pagesByTheLastArticleTimeThenIdDescendingFromTheCursor() {
		long early = storyline(3, "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z", 882_001);
		member(early, 882_001);
		long lateA = storyline(3, "2026-11-01T00:00:00Z", "2026-11-04T00:00:00Z", 882_002);
		member(lateA, 882_002);
		long lateB = storyline(3, "2026-11-01T00:00:00Z", "2026-11-04T00:00:00Z", 882_003);
		member(lateB, 882_003);
		long outside = storyline(3, "2026-11-01T00:00:00Z", "2026-11-09T00:00:00Z", 882_004);
		member(outside, 882_004);
		List<Long> events = List.of(882_001L, 882_002L, 882_003L);

		List<StoredStoryline> first = this.repository.page(events, null, null, 2);
		assertThat(first).extracting(StoredStoryline::id).containsExactly(lateB, lateA);
		StoredStoryline last = first.getLast();
		assertThat(this.repository.page(events, last.lastArticleAt(), last.id(), 2)).extracting(StoredStoryline::id)
			.containsExactly(early);
		assertThat(this.repository.page(List.of(), null, null, 2)).isEmpty();
	}

	private long storyline(int articles, String first, String last, long representative) {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('query-model', cast('[1,0]' as halfvec), :articles, :first, :last, :representative,
				        '2026-11-06T00:00:00Z', '2026-11-06T00:00:00Z')
				returning id
				""")
			.param("articles", articles)
			.param("first", Instant.parse(first).atOffset(ZoneOffset.UTC))
			.param("last", Instant.parse(last).atOffset(ZoneOffset.UTC))
			.param("representative", representative)
			.query(Long.class)
			.single();
	}

	private void member(long storyline, long event) {
		this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:storyline, :event, 'MERGE', '2026-11-06T00:00:00Z')
				""").param("storyline", storyline).param("event", event).update();
	}

	private void merge(long merged, long survivor) {
		this.jdbc.sql("update storylines set merged_into = :into, merged_at = now() where id = :id")
			.param("into", survivor)
			.param("id", merged)
			.update();
	}

}
