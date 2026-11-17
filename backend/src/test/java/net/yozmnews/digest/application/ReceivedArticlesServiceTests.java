package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.ReceivedArticles;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 받은 소식 — 날짜별로 보이는 다이제스트(그 날짜의 READY 가운데 ID 가 가장 큰 것)의 인용만, 받은 날짜는 가장 이른 날짜.
 * <p>
 * 지키는 규칙: digest-R13
 * <p>
 * 실제 PostgreSQL 위에서 다이제스트 · 섹션 · 인용 행을 직접 넣는다 — {@code EMPTY} · {@code FAILED} 다이제스트의 인용과 같은 날짜의 덮인
 * 다이제스트는 생성 흐름이 만들지 않는 입력이라 질의 조건을 보려면 행을 넣어야 한다. 인용의 기사는 행만 있으면 되므로 기사 테이블에 넣지 않는다(모듈
 * 사이라 FK 가 없다).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReceivedArticlesServiceTests {

	private static final UserId USER = new UserId(42);

	private static final UserId OTHER = new UserId(43);

	@Autowired
	ReceivedArticles received;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	@Test
	void onlyTheDigestShownForEachDateCountsAndTheEarliestDateWins() {
		// 11-01 — READY 둘: 작은 ID 는 기사 901(덮였다), 큰 ID 는 기사 902(보인다)
		cite(digest(USER, "2026-11-01", "READY"), 901);
		cite(digest(USER, "2026-11-01", "READY"), 902);
		// 11-02 — READY 하나: 기사 903, 기사 904
		long second = digest(USER, "2026-11-02", "READY");
		cite(second, 903);
		cite(second, 904);
		// 11-03 — READY: 기사 904 를 다시 인용한다(받은 날짜는 이른 11-02)
		cite(digest(USER, "2026-11-03", "READY"), 904);
		// 11-04 — EMPTY · FAILED 의 인용(기사 905)은 세지 않는다
		cite(digest(USER, "2026-11-04", "EMPTY"), 905);
		cite(digest(USER, "2026-11-04", "FAILED"), 905);
		// 다른 사용자의 READY(기사 906)는 이 사용자의 것이 아니다
		cite(digest(OTHER, "2026-11-01", "READY"), 906);

		Map<ArticleId, LocalDate> result = this.received.receivedAmong(USER, ids(901, 902, 903, 904, 905, 906, 907));

		assertThat(result).containsOnly(Map.entry(new ArticleId(902), LocalDate.parse("2026-11-01")),
				Map.entry(new ArticleId(903), LocalDate.parse("2026-11-02")),
				Map.entry(new ArticleId(904), LocalDate.parse("2026-11-02")));
		assertThat(this.received.receivedAmong(OTHER, ids(906))).containsOnly(
				Map.entry(new ArticleId(906), LocalDate.parse("2026-11-01")));
	}

	@Test
	void aLaterNonReadyDigestOfTheSameDateLeavesTheEarlierReadyShown() {
		// 대조 — 같은 날짜의 더 큰 ID 가 FAILED · RUNNING 이면 앞선 READY 가 보인다("ID 가 가장 큰 행"이 아니라 "ID 가 가장 큰 READY")
		cite(digest(USER, "2026-11-05", "READY"), 911);
		cite(digest(USER, "2026-11-05", "FAILED"), 912);
		digest(USER, "2026-11-05", "RUNNING");

		assertThat(this.received.receivedAmong(USER, ids(911, 912)))
			.containsOnly(Map.entry(new ArticleId(911), LocalDate.parse("2026-11-05")));
	}

	@Test
	void theLimitCountsDistinctArticles() {
		List<ArticleId> limit = new ArrayList<>(LongStream.rangeClosed(1, ReceivedArticles.MAX_ARTICLES)
			.mapToObj(ArticleId::new)
			.toList());
		cite(digest(USER, "2026-11-06", "READY"), 1);

		// 같은 기사가 두 번 와도 한 번으로 센다 — 1000개 + 반복 하나는 상한 안
		limit.add(new ArticleId(1));
		assertThat(this.received.receivedAmong(USER, limit))
			.containsOnly(Map.entry(new ArticleId(1), LocalDate.parse("2026-11-06")));
		List<ArticleId> over = new ArrayList<>(limit);
		over.add(new ArticleId(ReceivedArticles.MAX_ARTICLES + 1));
		assertThatIllegalArgumentException().isThrownBy(() -> this.received.receivedAmong(USER, over));
		assertThat(this.received.receivedAmong(USER, List.of())).isEmpty();
	}

	private long digest(UserId user, String date, String status) {
		boolean done = !status.equals("RUNNING");
		return this.jdbc.sql("""
				insert into digests (user_id, digest_date, period_start, period_end, status, trigger, error_code,
				    created_at, started_at, completed_at, version)
				values (:user, cast(:date as date), cast(:date as date) - interval '1 day', cast(:date as date), :status,
				    'SCHEDULED', :error, now(), now(), :completed, 0)
				returning id
				""")
			.param("user", user.value())
			.param("date", date)
			.param("status", status)
			.param("error", status.equals("FAILED") ? "GENERATOR_FAILED" : null)
			.param("completed", done ? Timestamp.from(Instant.now()) : null)
			.query(Long.class)
			.single();
	}

	private void cite(long digest, long article) {
		long section = this.jdbc.sql("""
				insert into digest_sections (digest_id, kind, position, title, body)
				values (:digest, 'MORE_HEADLINES', (select count(*) from digest_sections where digest_id = :digest), null, null)
				returning id
				""").param("digest", digest).query(Long.class).single();
		this.jdbc.sql("""
				insert into digest_items (digest_section_id, position, article_id, note, selection_reason)
				values (:section, 0, :article, null, 'SUBSCRIBED')
				""").param("section", section).param("article", article).update();
	}

	private static List<ArticleId> ids(long... values) {
		return LongStream.of(values).mapToObj(ArticleId::new).toList();
	}

}
