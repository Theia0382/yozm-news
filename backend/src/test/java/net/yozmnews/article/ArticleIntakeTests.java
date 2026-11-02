package net.yozmnews.article;

import static net.yozmnews.article.Fetched.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleRows.Entry;
import net.yozmnews.article.ArticleRows.Snapshot;
import net.yozmnews.article.application.ArticleIntake;
import net.yozmnews.source.ArticleFetched;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 들어온 항목의 판정 · 저장 · 기록 — 유스케이스를 같은 스레드에서 불러 커밋된 행과 발행된 이벤트를 잰다. 테스트를 트랜잭션으로
 * 감싸지 않고 테스트마다 두 테이블을 비운다.
 * <p>
 * 항목의 소스 · 피드는 실제 행으로 만든다 — 피드가 없으면 저장하지 않는다(article-R16).
 * <p>
 * 지키는 규칙: article-R3 · article-R4 · article-R5 · article-R7 · article-R10 · article-R11 · article-R16
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class ArticleIntakeTests {

	private static final Instant T = Instant.parse("2026-10-20T00:00:00Z");

	private static final String TITLE = "국회 본회의 통과";

	@Autowired
	ArticleIntake intake;

	@Autowired
	ApplicationEvents events;

	ArticleRows rows;

	SourceFeedRows sources;

	long sourceA;

	long sourceB;

	long feedA1;

	long feedA2;

	long feedA3;

	long feedB1;

	long feedB2;

	@BeforeEach
	void setUp(@Autowired JdbcClient jdbc) {
		this.rows = new ArticleRows(jdbc);
		this.rows.deleteAll();
		this.sources = new SourceFeedRows(jdbc);
		this.sources.deleteAll();
		this.sourceA = this.sources.source("a");
		this.feedA1 = this.sources.feed(this.sourceA, "a1");
		this.feedA2 = this.sources.feed(this.sourceA, "a2");
		this.feedA3 = this.sources.feed(this.sourceA, "a3");
		this.sourceB = this.sources.source("b");
		this.feedB1 = this.sources.feed(this.sourceB, "b1");
		this.feedB2 = this.sources.feed(this.sourceB, "b2");
	}

	@Test
	void anItemOfADeletedFeedIsNotStoredAndIsLogged(CapturedOutput output) {
		// 대조 — 남은 피드 · 숨긴 피드의 항목은 저장된다
		this.sources.hideFeed(this.feedA2);
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(item(this.sourceA, this.feedA2, "https://a.example/news/2", null, "국회 본회의 부결", T));
		List<Long> kept = this.rows.articleIds();
		assertThat(kept).hasSize(2);
		this.sources.deleteFeed(this.feedA3);

		this.intake.accept(item(this.sourceA, this.feedA3, "https://a.example/news/3", null, "지운 피드의 기사", T));

		assertThat(this.rows.articleIds()).isEqualTo(kept);
		assertThat(this.rows.entries()).containsExactly(new Entry(kept.get(0), this.feedA1, "NEW"),
				new Entry(kept.get(1), this.feedA2, "NEW"));
		assertThat(this.events.stream(ArticleStored.class)).hasSize(2);
		assertThat(output)
			.contains("지워진 피드의 항목이라 저장하지 않는다 — 소스 " + this.sourceA + ", 피드 " + this.feedA3);
	}

	@Test
	void processingTheSameEventTwiceChangesNothing() {
		ArticleFetched event = item(this.sourceA, this.feedA1, "https://a.example/news/1", "g-1", TITLE, T);

		this.intake.accept(event);
		Snapshot first = this.rows.snapshot();
		this.intake.accept(event);

		assertThat(this.rows.snapshot()).isEqualTo(first);
		long id = this.rows.articleIds().getFirst();
		assertThat(this.rows.entries()).containsExactly(new Entry(id, this.feedA1, "NEW"));
		assertThat(this.events.stream(ArticleStored.class)).containsExactly(new ArticleStored(new ArticleId(id)));
	}

	@Test
	void sameTitleFromAnotherSectionFeedJoinsTheArticleAndStaysThereWhenReprocessed() {
		ArticleFetched section = item(this.sourceA, this.feedA2, "https://a.example/section/9", null, "국회  본회의 통과",
				T.plusSeconds(3_600));
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(section);
		Snapshot first = this.rows.snapshot();

		this.intake.accept(section);

		assertThat(this.rows.snapshot()).isEqualTo(first);
		long id = this.rows.articleIds().getFirst();
		assertThat(this.rows.articleIds()).containsExactly(id);
		assertThat(this.rows.entries()).containsExactly(new Entry(id, this.feedA1, "NEW"), new Entry(id, this.feedA2, "TITLE"));
		assertThat(this.events.stream(ArticleStored.class)).hasSize(1);
	}

	@Test
	void reprocessingAnItemStaysWithItsArticleWhenACloserArticleAppearsLater() {
		// 기사 A(피드 A1, t) ← 항목 P(피드 A2, t+47h) TITLE. 기사 B(피드 A3, t+49h)는 A 와 49시간 차라 새 기사이고,
		// P 와는 2시간 차라 P 를 다시 처리할 때 더 가까운 후보가 된다
		ArticleFetched p = item(this.sourceA, this.feedA2, "https://a.example/section/9", null, TITLE, T.plusSeconds(47 * 3_600));
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(p);
		this.intake.accept(item(this.sourceA, this.feedA3, "https://a.example/other/5", null, TITLE, T.plusSeconds(49 * 3_600)));

		this.intake.accept(p);

		assertThat(this.rows.articleIds()).hasSize(2);
		long a = this.rows.articleIds().get(0);
		long b = this.rows.articleIds().get(1);
		assertThat(this.rows.entries()).containsExactly(new Entry(a, this.feedA1, "NEW"), new Entry(a, this.feedA2, "TITLE"),
				new Entry(b, this.feedA3, "NEW"));
		assertThat(this.events.stream(ArticleStored.class)).containsExactly(new ArticleStored(new ArticleId(a)),
				new ArticleStored(new ArticleId(b)));
	}

	@Test
	void sameTitleInTheSameFeedNextDayIsANewArticle() {
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, "[게시판]", T));
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/2", null, "[게시판]", T.plusSeconds(24 * 3_600)));

		assertThat(this.rows.articleIds()).hasSize(2);
		assertThat(this.rows.entries()).extracting(Entry::matchKind).containsExactly("NEW", "NEW");
		assertThat(this.events.stream(ArticleStored.class)).hasSize(2);
	}

	@Test
	void guidJoinsOnlyWithTheSameTitle(CapturedOutput output) {
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", "g-7", TITLE, T));
		// 같은 피드 · 같은 guid · 같은 제목, URL 이 바뀌었고 시간 창 밖이다 — guid 로만 같은 기사
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1-v2", "g-7", TITLE, T.plusSeconds(72 * 3_600)));
		// 같은 피드 · 같은 guid 이지만 제목이 다르다 — guid 를 재사용하는 피드
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/2", "g-7", "국회 본회의 부결", T.plusSeconds(73 * 3_600)));

		assertThat(this.rows.articleIds()).hasSize(2);
		long a = this.rows.articleIds().get(0);
		long reused = this.rows.articleIds().get(1);
		assertThat(this.rows.entries()).containsExactly(new Entry(a, this.feedA1, "NEW"), new Entry(reused, this.feedA1, "NEW"));
		assertThat(this.events.stream(ArticleStored.class)).hasSize(2);
		assertThat(output).contains("같은 피드에 같은 guid 의 다른 제목 기사가 있어 새 기사로 저장했다");
	}

	@Test
	void sameCanonicalUrlFromAnotherSourceJoinsTheArticle() {
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1?utm_source=rss", null, TITLE, T));
		this.intake.accept(item(this.sourceB, this.feedB1, "HTTPS://A.example/news/1#top", null, "다른 제목", T));

		long id = this.rows.articleIds().getFirst();
		assertThat(this.rows.articleIds()).containsExactly(id);
		assertThat(this.rows.entries()).containsExactly(new Entry(id, this.feedA1, "NEW"), new Entry(id, this.feedB1, "URL"));
		assertThat(this.events.stream(ArticleStored.class)).hasSize(1);
	}

	@Test
	void sameTitleFromAnotherFeedOfASourceThatSharesTheArticleJoinsIt() {
		// 소스 A 의 피드 A1 이 먼저 저장하고 소스 B 의 피드 B1 이 같은 URL 로 붙은 기사 — 소스 B 의 다른 피드 B2 의 같은 제목 항목
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(item(this.sourceB, this.feedB1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(item(this.sourceB, this.feedB2, "https://a.example/section/9", null, TITLE, T.plusSeconds(3_600)));

		long id = this.rows.articleIds().getFirst();
		assertThat(this.rows.articleIds()).containsExactly(id);
		assertThat(this.rows.entries()).containsExactly(new Entry(id, this.feedA1, "NEW"), new Entry(id, this.feedB1, "URL"),
				new Entry(id, this.feedB2, "TITLE"));
	}

	@Test
	void sameTitleFromAnotherSourceWithoutAnEntryOfThatSourceIsANewArticle() {
		// 소스 B 의 엔트리가 없는 소스 A 의 기사는 같은 제목이어도 소스 B 의 피드 B2 항목이 붙을 후보가 아니다
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(item(this.sourceB, this.feedB2, "https://a.example/section/9", null, TITLE, T.plusSeconds(3_600)));

		List<Long> ids = this.rows.articleIds();
		assertThat(ids).hasSize(2);
		assertThat(this.rows.entries()).containsExactly(new Entry(ids.get(0), this.feedA1, "NEW"),
				new Entry(ids.get(1), this.feedB2, "NEW"));
	}

	@Test
	void sameTitleFromTheFeedThatAlreadyHasTheSharedArticleIsANewArticle() {
		this.intake.accept(item(this.sourceA, this.feedA1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(item(this.sourceB, this.feedB1, "https://a.example/news/1", null, TITLE, T));
		this.intake.accept(item(this.sourceB, this.feedB1, "https://a.example/section/9", null, TITLE, T.plusSeconds(3_600)));

		List<Long> ids = this.rows.articleIds();
		assertThat(ids).hasSize(2);
		assertThat(this.rows.entries()).containsExactly(new Entry(ids.get(0), this.feedA1, "NEW"),
				new Entry(ids.get(0), this.feedB1, "URL"), new Entry(ids.get(1), this.feedB1, "NEW"));
	}

}
