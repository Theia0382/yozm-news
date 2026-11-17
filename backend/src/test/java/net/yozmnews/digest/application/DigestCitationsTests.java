package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.feedback.DigestCitations;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 인용 확인 — 주어진 다이제스트가 그 사용자의 것이고 그 기사를 인용했는가. 다이제스트의 상태 · 날짜를 보지 않는다.
 * <p>
 * 지키는 규칙: digest-R14
 * <p>
 * 실제 PostgreSQL 위에서 다이제스트 · 섹션 · 인용 행을 직접 넣는다({@link DigestRows}) — 같은 날짜의 덮인 다이제스트는 생성 흐름이 만들지
 * 않는 입력이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestCitationsTests {

	private static final UserId USER = new UserId(42);

	private static final UserId OTHER = new UserId(43);

	@Autowired
	DigestCitations citations;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	@Test
	void aDigestCitesOnlyTheArticlesInItsOwnCitations() {
		long first = digest(USER, "2026-11-01");
		cite(first, 901);
		long second = digest(USER, "2026-11-02");
		cite(second, 902);

		assertThat(this.citations.cites(USER, first, new ArticleId(901))).isTrue();
		// 인용하지 않은 기사 — 같은 사용자의 다른 다이제스트만 인용했다
		assertThat(this.citations.cites(USER, first, new ArticleId(902))).isFalse();
		// 대조 — 그 기사를 인용한 다이제스트로 물으면 참이다(거짓이 "그 기사는 언제나 거짓"이어서가 아니다)
		assertThat(this.citations.cites(USER, second, new ArticleId(902))).isTrue();
		// 어느 다이제스트도 인용하지 않은 기사
		assertThat(this.citations.cites(USER, first, new ArticleId(903))).isFalse();
	}

	@Test
	void anotherUsersDigestIsNotMine() {
		long mine = digest(USER, "2026-11-01");
		cite(mine, 901);
		long theirs = digest(OTHER, "2026-11-01");
		cite(theirs, 901);

		assertThat(this.citations.cites(USER, theirs, new ArticleId(901))).isFalse();
		assertThat(this.citations.cites(OTHER, mine, new ArticleId(901))).isFalse();
		// 대조 — 자기 다이제스트로는 둘 다 참이다
		assertThat(this.citations.cites(USER, mine, new ArticleId(901))).isTrue();
		assertThat(this.citations.cites(OTHER, theirs, new ArticleId(901))).isTrue();
	}

	@Test
	void aMissingDigestCitesNothing() {
		long digest = digest(USER, "2026-11-01");
		cite(digest, 901);

		// 대조 — 넣은 다이제스트로는 참이다(아래 거짓이 인용 행이 없어서가 아니다)
		assertThat(this.citations.cites(USER, digest, new ArticleId(901))).isTrue();
		assertThat(this.citations.cites(USER, digest + 1000, new ArticleId(901))).isFalse();
		assertThat(this.citations.cites(USER, 0, new ArticleId(901))).isFalse();
		assertThat(this.citations.cites(USER, -1, new ArticleId(901))).isFalse();
	}

	@Test
	void aDigestCoveredByALaterOneOfTheSameDateStillCites() {
		// 같은 날짜의 READY 둘 — 작은 ID 는 날짜별로 보이는 다이제스트에 덮였다
		long covered = digest(USER, "2026-11-01");
		cite(covered, 901);
		long shown = digest(USER, "2026-11-01");
		cite(shown, 902);

		assertThat(this.citations.cites(USER, covered, new ArticleId(901))).isTrue();
		assertThat(this.citations.cites(USER, shown, new ArticleId(902))).isTrue();
		// 덮인 다이제스트의 인용은 보이는 다이제스트의 인용이 아니다 — 다이제스트 하나만 본다
		assertThat(this.citations.cites(USER, shown, new ArticleId(901))).isFalse();
	}

	private long digest(UserId user, String date) {
		return DigestRows.digest(this.jdbc, user.value(), date);
	}

	private void cite(long digest, long article) {
		DigestRows.cite(this.jdbc, digest, article);
	}

}
