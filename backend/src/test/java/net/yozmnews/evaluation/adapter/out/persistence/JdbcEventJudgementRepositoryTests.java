package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 사건 소속 판정 원본 저장소 — 행 · {@code jsonb} 쓰기, 기간 읽기, 지운 기사 따르기를 Flyway 가 적용된 실제 PostgreSQL 에서 본다. 테스트는
 * 테스트 트랜잭션 안에서 돈다.
 * <p>
 * 지키는 규칙: evaluation-R21 · evaluation-R38 · evaluation-R39
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEventJudgementRepository.class })
class JdbcEventJudgementRepositoryTests {

	private static final Instant AT = Instant.parse("2026-11-05T06:30:00Z");

	private static final String RAW = "{\"model\":\"jev-1.14.0\",\"answers\":{"
			+ "\"event.12\":{\"type\":\"noul\",\"noul\":0.83},"
			+ "\"event.15\":{\"type\":\"noul\",\"noul\":0.07}},\"usage\":{\"input_tokens\":812,\"output_tokens\":40}}";

	@Autowired
	JdbcEventJudgementRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void writesOneRowWithTheAskedCandidatesInRequestOrder() {
		this.repository.insert(record(5, List.of(new JudgedEvent(15, List.of(new ArticleId(7)), 0.07),
				new JudgedEvent(12, List.of(new ArticleId(3), new ArticleId(4)), 0.83))));

		Map<String, Object> row = this.jdbc.sql("""
				select article_id, evaluator, model_version, wording_version, candidates::text as candidates,
				       raw_response = cast(:raw as jsonb) as same_raw, input_tokens
				from article_event_judgements
				""").param("raw", RAW).query().singleRow();

		assertThat(row).containsEntry("article_id", 5L)
			.containsEntry("evaluator", "jev")
			.containsEntry("model_version", "jev-1.14.0")
			.containsEntry("wording_version", "membership-1")
			.containsEntry("candidates", "[{\"eventId\": 15, \"articleIds\": [7], \"probability\": 0.07}, "
					+ "{\"eventId\": 12, \"articleIds\": [3, 4], \"probability\": 0.83}]")
			.containsEntry("same_raw", true)
			.containsEntry("input_tokens", 812);
		assertThat(this.jdbc.sql("select judged_at from article_event_judgements")
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(AT);
	}

	@Test
	void returnsTheIdOfTheInsertedRow() {
		EventJudgementId first = this.repository
			.insert(record(5, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.83))));
		EventJudgementId second = this.repository
			.insert(record(6, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.79))));

		assertThat(this.jdbc.sql("select article_id from article_event_judgements where id = :id")
			.param("id", first.value())
			.query(Long.class)
			.single()).isEqualTo(5L);
		assertThat(this.jdbc.sql("select article_id from article_event_judgements where id = :id")
			.param("id", second.value())
			.query(Long.class)
			.single()).isEqualTo(6L);
	}

	@Test
	void readsThePeriodNewestFirstWithItsCandidates() {
		Instant from = Instant.parse("2026-11-01T00:00:00Z");
		Instant to = Instant.parse("2026-11-08T00:00:00Z");
		EventJudgementId atFrom = this.repository.insert(record(5,
				List.of(new JudgedEvent(15, List.of(new ArticleId(7)), 0.07),
						new JudgedEvent(12, List.of(new ArticleId(3), new ArticleId(4)), 0.83)),
				from));
		this.repository.insert(record(6, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.5)), to));
		this.repository.insert(
				record(7, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.5)), from.minusNanos(1_000)));
		EventJudgementId later = this.repository
			.insert(record(8, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.6)), AT));

		List<EventJudgementView> page = this.repository.page(from, to, null, null, 10);

		assertThat(page).extracting(EventJudgementView::id).containsExactly(later, atFrom);
		assertThat(page.get(1)).isEqualTo(new EventJudgementView(atFrom, new ArticleId(5),
				List.of(new JudgedEvent(15, List.of(new ArticleId(7)), 0.07),
						new JudgedEvent(12, List.of(new ArticleId(3), new ArticleId(4)), 0.83)),
				"jev", "jev-1.14.0", "membership-1", 812, from));
	}

	@Test
	void ordersTheSameTimeByIdAndContinuesBelowTheCursor() {
		Instant from = Instant.parse("2026-11-01T00:00:00Z");
		Instant to = Instant.parse("2026-11-08T00:00:00Z");
		EventJudgementId a = this.repository
			.insert(record(5, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.5)), AT));
		EventJudgementId b = this.repository
			.insert(record(6, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.5)), AT));
		EventJudgementId c = this.repository.insert(
				record(7, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.5)), AT.minusSeconds(60)));

		List<EventJudgementView> first = this.repository.page(from, to, null, null, 2);
		List<EventJudgementView> second = this.repository.page(from, to, first.getLast().judgedAt(),
				first.getLast().id().value(), 2);
		// 커서가 같은 시각의 행에 있으면 그 시각의 더 작은 ID 부터 잇는다
		List<EventJudgementView> withinTheSameTime = this.repository.page(from, to, AT, b.value(), 2);

		assertThat(first).extracting(EventJudgementView::id).containsExactly(b, a);
		assertThat(second).extracting(EventJudgementView::id).containsExactly(c);
		assertThat(withinTheSameTime).extracting(EventJudgementView::id).containsExactly(a, c);
	}

	@Test
	void keepsEveryJudgementOfTheSameArticle() {
		this.repository.insert(record(5, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.83))));
		this.repository.insert(record(5, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.79))));

		assertThat(rowsOf(5)).isEqualTo(2);
	}

	@Test
	void deletesTheJudgementsOfTheGivenArticlesOnly() {
		this.repository.insert(record(5, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.83))));
		this.repository.insert(record(5, List.of(new JudgedEvent(15, List.of(new ArticleId(7)), 0.07))));
		this.repository.insert(record(6, List.of(new JudgedEvent(12, List.of(new ArticleId(5)), 0.9))));

		this.repository.deleteByArticles(List.of(new ArticleId(5)));

		assertThat(rowsOf(5)).isZero();
		assertThat(rowsOf(6)).isEqualTo(1);
	}

	@Test
	void deletesNothingForNoArticles() {
		this.repository.insert(record(5, List.of(new JudgedEvent(12, List.of(new ArticleId(3)), 0.83))));

		this.repository.deleteByArticles(List.of());

		assertThat(rowsOf(5)).isEqualTo(1);
	}

	private static MembershipRecord record(long article, List<JudgedEvent> candidates) {
		return record(article, candidates, AT);
	}

	private static MembershipRecord record(long article, List<JudgedEvent> candidates, Instant judgedAt) {
		return new MembershipRecord(new ArticleId(article), candidates, "jev", "jev-1.14.0", "membership-1", 812, RAW,
				judgedAt);
	}

	private long rowsOf(long article) {
		return this.jdbc.sql("select count(*) from article_event_judgements where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

}
