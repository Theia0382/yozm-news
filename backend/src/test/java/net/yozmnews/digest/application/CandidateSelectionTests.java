package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.InputEvent;
import net.yozmnews.digest.domain.input.InputItem;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.TopicSubscriptionHistory;
import net.yozmnews.user.UserStorylineDirectory;
import net.yozmnews.user.UserStorylineFixtures;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 후보 선별의 재료 경로 — 실제 PostgreSQL 의 사용자 설정 · 기사 · 평가 · 사건 · 스토리라인 행에서 관심 설정 조회 · 첫 평가 시각 · 평가 · 발행
 * 시각 · 소스 · 사건 · 사건의 소속 기사 평가 · 꼭대기 · 내용 조회를 거쳐 규칙까지. 기본 체계(버전 1)로 본다. 기간은 2026-11-05 하루(UTC)이고
 * 기사는 모두 그날 0시 발행이다({@link EventArticles#insert}).
 * <p>
 * 지키는 규칙: digest-R15 ~ digest-R19 · digest-R21 · digest-R22 · digest-R25 · digest-R44 ~ digest-R46 · digest-R54 — 후보는 사건(사건 없는 기사는 버림), 구독 필터는 사건의 주제 · 지역 제외는 사건의 지역 · 기사 제외는 기사마다, 사건 중요도는 소속
 * 기사의 최대, 표본(기간에 든 · 제외를 지난 기사), 상한 · 선정 이유, 항목 묶기(꼭대기), 구독 행마다 주요 뉴스 상한(사용자의 구독 행), 관심 설정과 계보 표의 활성 버전 대조, 관심도 순위와
 * {@code INTEREST}(노출 · 좋아요 · 주제 구독 변경 · 스토리라인 구독 변경), 싫어요 거름(구독 사건 · 큰 슬롯 사건 — 합쳐진 사건의 싫어요 포함), 큰 슬롯의 행 순서(관심을 곱한 순서 · 항목의 꼭대기) · 주제 거름, 중립
 * 결과의 토큰 어림,
 * 관심도 재료를 읽는 범위의 끝(부르는 시각), 생성의 입력(프로필 · 항목 · 사건 · 표본 기사 재료)
 * <p>
 * 관심도의 기준 시각은 기간 끝 11-06 0시다. 노출 · 반응 · 구독 변경은 그 앞 하루 남짓이라 무게가 1 에 가깝고, 기대값은 순위가 바뀌는지만 본다
 * — 무게의 값은 산식 단위 시험(InterestFormulaTests)이 고정한다.
 * <p>
 * 토큰 어림: 기사 줄 = 제목 + 요약문 "요약" 2 + 80, 사건 = 기사 줄의 합 + 40. 사건 E1(기사 "정치 하나" 5 · "정치 둘" 4) = 87 + 86 + 40 =
 * 213, 제목 다섯 글자 기사 하나뿐인 사건 = 87 + 40 = 127.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class CandidateSelectionTests {

	@Autowired
	JobScheduler digestJobs;

	private static final DigestPeriod DAY = new DigestPeriod(Instant.parse("2026-11-05T00:00:00Z"),
			Instant.parse("2026-11-06T00:00:00Z"));

	private static final LlmModel ROOMY = new LlmModel(Provider.OPENAI, "gpt-test", 32_000, true);

	/** 지시 6,000 + 프로필(구독 행 1 × 20 + 고정 틀 200) + 사건 E1 213 — 사건 E1 하나만 든다 */
	private static final LlmModel ONE_CANDIDATE = new LlmModel(Provider.OPENAI, "gpt-test", 6_000 + 20 + 200 + 213,
			true);

	@Autowired
	CandidateSelection selection;

	@Autowired
	DigestInterestsDirectory interests;

	@Autowired
	ArticleEvaluations evaluations;

	@Autowired
	TagKeyLineage lineage;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	EventDirectory events;

	@Autowired
	StorylineDirectory storylines;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	InterestMaterials interestMaterials;

	@Autowired
	EventFeedback feedback;

	@Autowired
	ExposureRepository exposures;

	@Autowired
	TopicSubscriptionHistory topicHistory;

	@Autowired
	StorylineSubscriptionDirectory storylineSubscriptions;

	@Autowired
	UserStorylineDirectory userStorylines;

	private ReaderRows rows;

	/** 기사 → 그 기사만 가진 사건(시험이 만든 것) */
	private final Map<Long, Long> soloEvents = new HashMap<>();

	private long user;

	private long a1;

	private long a2;

	private long a4;

	private long a5;

	private long a6;

	/** 기사 1 · 2 의 사건 */
	private long e1;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@BeforeEach
	void prepare() {
		this.rows = new ReaderRows(this.jdbc);
		clean();
		this.user = user();
		// 1 · 2: 같은 사건 E1, 정치 행정 — 사건 중요도는 최대 4.0
		this.a1 = article("정치 하나", "politics.executive", 4.0, "2026-11-05T06:00:00Z", 1);
		this.a2 = article("정치 둘", "politics.executive", 3.0, "2026-11-05T06:00:00Z", 1);
		this.e1 = this.rows.event(2, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		this.rows.assign(this.a1, this.e1, "2026-11-05T00:00:00Z");
		this.rows.assign(this.a2, this.e1, "2026-11-05T00:00:00Z");
		// 3: 제외 소스 2 만 · 사건 없음 — 빠짐(사건에 배정하지 않는다 — 기간에 사건 기사가 있는 소스가 늘면 큰 판정의 보도 강도가
		// 바뀐다). 4: 구독 밖 주제 — 빠짐. 5: 혼자인 사건 — 남음. 6: 첫 평가가 기간 전 — 빠짐
		article("정치 셋", "politics.legislature", 2.0, "2026-11-05T06:00:00Z", 2);
		this.a4 = eventArticle("시장", "economy.markets", 5.0, "2026-11-05T06:00:00Z", 1);
		this.a5 = eventArticle("정치 다섯", "politics.executive", 1.0, "2026-11-05T06:00:00Z", 1);
		this.a6 = eventArticle("정치 여섯", "politics.executive", 5.0, "2026-11-04T23:59:59Z", 1);
	}

	@AfterEach
	void clean() {
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from feedback").update();
		this.jdbc.sql("delete from feedback_events").update();
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		this.rows.clear();
		EventArticles.clear(this.jdbc);
		this.jdbc.sql("delete from users").update();
		this.soloEvents.clear();
	}

	@Test
	void theSubscribedEventsOfThePeriodComeOnceEachInRankOrderWithTheirArticles() {
		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// 기사 1 · 2 는 같은 사건이라 사건 하나, 그 기사 둘이 표본이다
		assertThat(events(input)).containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
		assertThat(articles(input)).containsExactly(this.a1, this.a2, this.a5);
	}

	@Test
	void anArticleWithoutAnEventIsNotACandidate() {
		// 구독 주제 · 중요도 5 · 기간에 첫 평가된 기사지만 사건에 배정되지 않았다
		long unassigned = article("정치 미배정", "politics.executive", 5.0, "2026-11-05T06:00:00Z", 1);

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(articles(input)).doesNotContain(unassigned);
		// 대조 — 사건에 배정된 기간 기사 둘은 한 사건으로 들고 둘 다 그 사건의 표본이다
		assertThat(events(input)).contains(this.e1 + ":SUBSCRIBED");
		assertThat(articlesOf(input, this.e1)).containsExactly(this.a1, this.a2);
	}

	@Test
	void theSubscriptionIsMatchedOnTheTopicOfTheEventNotOnTheTopicOfItsArticleInThePeriod() {
		// 사건 X: 기간 기사는 금융(구독 밖), 기간 앞에 평가된 소속 기사 둘은 정치 행정 — 사건의 주제는 정치 행정(2/3)
		long outsideTopic = article("금융 기사", "economy.finance", 3.5, "2026-11-05T06:00:00Z", 1);
		long x = eventOf(outsideTopic, article("지난 정치 하나", "politics.executive", 3.0, "2026-11-01T00:00:00Z", 1),
				article("지난 정치 둘", "politics.executive", 3.0, "2026-11-01T00:00:00Z", 1));
		// 대조 — 사건 Y: 기간 기사 · 소속 기사 모두 정치 행정
		long insideTopic = article("정치 기사", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1);
		long y = eventOf(insideTopic, article("지난 정치 셋", "politics.executive", 3.0, "2026-11-01T00:00:00Z", 1));

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(input)).contains(x + ":SUBSCRIBED", y + ":SUBSCRIBED");
		// 표본은 기간에 든 기사만 — 기간 앞에 평가된 소속 기사는 싣지 않는다
		assertThat(articlesOf(input, x)).containsExactly(outsideTopic);
		assertThat(articlesOf(input, y)).containsExactly(insideTopic);
	}

	@Test
	void articleExclusionsAreAppliedPerArticleAndTheRegionExclusionPerEvent() {
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'REGION', 'us', 'EXCLUDE', now())
				""").param("user", this.user).update();
		// 사건 S: 제외 소스 2 의 기사와 소스 1 의 기사 — 사건은 후보, 표본에는 소스 1 의 기사만
		long fromExcluded = article("제외 소스 기사", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 2);
		long fromKept = article("남는 소스 기사", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1);
		long s = eventOf(fromExcluded, fromKept);
		// 사건 R: 소속 둘 가운데 하나가 미국(몫 1/2) — 사건의 지역이 제외라 후보가 아니다
		long r = eventOf(region(article("미국 기사", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1), "us"),
				article("국내 기사", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1));
		// 대조 — 사건 Q: 소속 셋 가운데 하나가 미국(몫 1/3) — 후보이고 미국 기사도 표본에 든다
		long american = region(article("미국 기사 둘", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1), "us");
		long q = eventOf(american, article("국내 기사 둘", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1),
				article("국내 기사 셋", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1));
		// 대조 — 사건 Z: 제외 소스의 기사뿐 — 후보가 아니다
		long z = eventOf(article("제외 소스 기사 둘", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 2));

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(input)).contains(s + ":SUBSCRIBED", q + ":SUBSCRIBED")
			.doesNotContain(r + ":SUBSCRIBED", z + ":SUBSCRIBED");
		assertThat(articlesOf(input, s)).containsExactly(fromKept);
		assertThat(articlesOf(input, q)).contains(american).hasSize(3);
		assertThat(articles(input)).doesNotContain(fromExcluded);
	}

	@Test
	void theEventImportanceIsTheMaximumOfItsMembersEvenOutsideThePeriod() {
		// 사건 W: 기간 기사 중요도 2 · 기간 앞에 평가된 소속 기사 중요도 4 → 사건 중요도 4. 사건 V: 기사 하나 중요도 3.5
		long w = eventOf(article("넓은 사건", "politics.executive", 2.0, "2026-11-05T06:00:00Z", 1),
				article("지난 큰 기사", "politics.executive", 4.0, "2026-11-01T00:00:00Z", 1));
		long v = event(eventArticle("좁은 사건", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1));

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// E1(4) · W(4 — 같은 점수 · 같은 발행 시각이라 사건 ID 순) · V(3.5) · 사건 5(1). 대조 — 기사 하나씩인 V(3.5) 가 사건 5(1) 앞
		assertThat(events(input)).containsExactly(this.e1 + ":SUBSCRIBED", w + ":SUBSCRIBED", v + ":SUBSCRIBED",
				event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void eventsOfOneStorylineTreeMakeOneItem() {
		// E1 과 사건 5 가 한 스토리라인의 직접 소속 — 기간 안 중요도 합 8 · 소스 하나라 크지 않다
		long storyline = this.rows.storyline("2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z", this.e1);
		this.rows.member(storyline, this.e1);
		this.rows.member(storyline, event(this.a5));
		long alone = event(eventArticle("혼자 사건", "politics.executive", 2.0, "2026-11-05T06:00:00Z", 1));

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// 항목 점수는 사건 점수의 최대 — 나무의 항목(4) 다음 혼자인 사건(2), 항목 안 사건은 사건 점수 순
		assertThat(input.items()).extracting(item -> item.events().stream().map(e -> e.event().value()).toList())
			.containsExactly(List.of(this.e1, event(this.a5)), List.of(alone));
		assertThat(input.items()).extracting(InputItem::kind).containsOnly(ItemKind.TOP_STORY);
		// 항목은 자기를 묶은 꼭대기를 싣는다 — 혼자 한 사건의 항목은 없다
		assertThat(input.items()).extracting(InputItem::storyline).containsExactly(new StorylineId(storyline), null);
	}

	@Test
	void aSubscribedRowTakesAtMostTwoTopStoriesOnceThreeRowsHaveItems() {
		// 경제 · 국제도 구독해 세 행 — 시장(경제 5) · 정치 4.9 · 4.8 · 4.7 · E1(정치 4) · 분쟁(국제 2) · 사건 5(정치 1)
		subscribeTopic("economy");
		subscribeTopic("world");
		long p1 = event(eventArticle("정치 일", "politics.executive", 4.9, "2026-11-05T06:00:00Z", 1));
		long p2 = event(eventArticle("정치 이", "politics.executive", 4.8, "2026-11-05T06:00:00Z", 1));
		long p3 = event(eventArticle("정치 삼", "politics.executive", 4.7, "2026-11-05T06:00:00Z", 1));
		long conflict = event(eventArticle("분쟁", "world.conflict", 2.0, "2026-11-05T06:00:00Z", 1));

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// 1차: 시장 · 정치 4.9 · 4.8 · 분쟁 — 정치 4.7 · E1 · 사건 5 는 정치 몫이 차 건너뛴다. 2차: 남은 한 자리는 순위대로 정치 4.7.
		// 순위만 보면 분쟁 대신 E1 이 주요 뉴스다
		assertThat(input.items()).extracting(item -> item.events().getFirst().event().value(), InputItem::kind)
			.containsExactly(tuple(event(this.a4), ItemKind.TOP_STORY), tuple(p1, ItemKind.TOP_STORY),
					tuple(p2, ItemKind.TOP_STORY), tuple(p3, ItemKind.TOP_STORY), tuple(conflict, ItemKind.TOP_STORY),
					tuple(this.e1, ItemKind.HEADLINE), tuple(event(this.a5), ItemKind.HEADLINE));
	}

	@Test
	void theInputCarriesTheProfileAndTheArticleMaterialWithActiveNames() {
		this.jdbc.sql("""
				update user_settings set interest_text = '반도체 공급망', timezone = 'America/New_York',
				  summary_length = 'DETAILED', reasoning_effort = 'LOW'
				where user_id = :user
				""").param("user", this.user).update();
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'REGION', 'japan', 'EXCLUDE', now()), (:user, 'REGION', 'us', 'EXCLUDE', now())
				""").param("user", this.user).update();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				select id, article_id, 'REGION', region, 0.9 from article_evaluations,
				  (values ('china'), ('korean-peninsula')) as regions(region)
				where article_id = :article
				""").param("article", this.a1).update();

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// 이름은 기본 체계 파일(tag-sets/default.yaml)에서 손으로 옮겨 적은 것이다. 지역은 파일 순서(국내 · 북한·한반도 · 미국 · 중국 ·
		// 일본 …)로 늘어놓는다 — 키의 사전 순(china < korean-peninsula, japan < us)과 다르다
		assertThat(input.profile()).isEqualTo(new DigestProfile(List.of("정치"), List.of("미국", "일본"), "반도체 공급망",
				ZoneId.of("America/New_York"), SummaryLength.DETAILED, ReasoningEffort.LOW));
		InputEvent firstEvent = input.items().getFirst().events().getFirst();
		assertThat(firstEvent.event()).isEqualTo(new EventId(this.e1));
		assertThat(firstEvent.reason()).isEqualTo(SelectionReason.SUBSCRIBED);
		CandidateArticle first = firstEvent.articles().getFirst();
		assertThat(first.candidate()).isEqualTo(new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED));
		assertThat(first.title()).isEqualTo("정치 하나");
		assertThat(first.summary()).isEqualTo("요약");
		assertThat(first.topics()).containsExactly("대통령실·행정부");
		assertThat(first.regions()).containsExactly("북한·한반도", "중국");
	}

	@Test
	void aPeriodWithoutArticlesStillGivesTheProfile() {
		DigestPeriod empty = new DigestPeriod(Instant.parse("2026-12-05T00:00:00Z"),
				Instant.parse("2026-12-06T00:00:00Z"));

		DigestInput input = this.selection.select(new UserId(this.user), empty, ROOMY).orElseThrow();

		assertThat(input.items()).isEmpty();
		assertThat(input.profile().topics()).containsExactly("정치");
	}

	@Test
	void theInputTokenBudgetCutsTheTailByEvent() {
		// 예산 = 지시 6,000 + 프로필 220 + 사건 E1 213 → 사건 E1 하나만(사건 5 의 127 은 들 자리가 없다)
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");
	}

	@Test
	void aPeriodLongerThanTheReadLimitIsReadBackFromItsEnd() {
		// 9월 1일 ~ 11월 6일(66일)은 평가 조회의 상한(32일)을 넘는다 — 끝에서 32일 앞인 10월 5일 0시부터 읽는다.
		// 6(첫 평가 11월 4일)은 들고 7(첫 평가 10월 1일)은 빠진다. 8 은 첫 평가가 11월 1일이지만 발행이 10월 1일이라 줄인 시작의
		// 24시간 전(10월 4일 0시)보다 앞서 빠진다 — 발행 시각의 하한도 줄인 시작에서 잰다
		long a7 = eventArticle("정치 일곱", "politics.executive", 5.0, "2026-10-01T00:00:00Z", 1);
		long a8 = eventArticle("정치 여덟", "politics.executive", 5.0, "2026-11-01T00:00:00Z", 1);
		this.jdbc.sql("update articles set published_at = '2026-10-01T00:00:00Z' where id = :id").param("id", a8).update();
		DigestPeriod long66Days = new DigestPeriod(Instant.parse("2026-09-01T00:00:00Z"),
				Instant.parse("2026-11-06T00:00:00Z"));

		DigestInput input = this.selection.select(new UserId(this.user), long66Days, ROOMY).orElseThrow();

		assertThat(events(input)).containsExactly(event(this.a6) + ":SUBSCRIBED", this.e1 + ":SUBSCRIBED",
				event(this.a5) + ":SUBSCRIBED");
		assertThat(articles(input)).doesNotContain(a7, a8);
	}

	@Test
	void aBigStorylineOutsideTheSubscriptionsComesFirstAsTheBigSlotWithItsArticles() {
		long[] conflict = bigStoryline();

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// 큰 슬롯 사건(사건 중요도 5)이 먼저 — 표본은 그 사건의 기간 안 기사 셋, 그 다음 구독 사건
		assertThat(events(input)).containsExactly(event(conflict[0]) + ":MAJOR_STORYLINE", this.e1 + ":SUBSCRIBED",
				event(this.a5) + ":SUBSCRIBED");
		assertThat(articlesOf(input, event(conflict[0]))).containsExactly(conflict[0], conflict[1], conflict[2]);
		assertThat(input.items().getFirst().kind()).isEqualTo(ItemKind.TOP_STORY);
		assertThat(input.items().getFirst().events().getFirst().articles().getFirst().topics()).containsExactly("분쟁·안보");
	}

	@Test
	void theBudgetKeepsTheBigSlotWithinHalfOfWhatIsLeftOnceSubscribedEventsAreCut() {
		long[] conflict = bigStoryline();
		// 큰 슬롯 사건 = 87("분쟁 하나") + 86 + 86 + 40 = 299, 구독 사건 E1 213 · 사건 5 127. 후보 몫 299: 사건 5(구독 꼬리 — 남는 2 의 절반
		// 1 = 큰 1) → 큰 슬롯 사건(남는 1 의 절반 0 < 큰 1) 순으로 빠져 E1 만 든다
		LlmModel tight = new LlmModel(Provider.OPENAI, "gpt-test", 6_000 + 220 + 299, true);

		assertThat(events(this.selection.select(new UserId(this.user), DAY, tight)))
			.containsExactly(this.e1 + ":SUBSCRIBED");

		// 대조: 후보 몫 512(= 299 + 213)면 사건 5 만 빠지고 큰 슬롯 사건과 E1 이 든다
		LlmModel twoFit = new LlmModel(Provider.OPENAI, "gpt-test", 6_000 + 220 + 299 + 213, true);
		assertThat(events(this.selection.select(new UserId(this.user), DAY, twoFit)))
			.containsExactly(event(conflict[0]) + ":MAJOR_STORYLINE", this.e1 + ":SUBSCRIBED");
	}

	@Test
	void zeroBigSlotsLeaveTheBigStorylineOut() {
		bigStoryline();
		this.jdbc.sql("update user_settings set big_slots = 0 where user_id = :user").param("user", this.user).update();

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(input)).containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void aBigEventWhoseMembersAreMostlyInAnExcludedRegionHasNoBigSlot() {
		long[] conflict = bigStoryline();
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'REGION', 'us', 'EXCLUDE', now())
				""").param("user", this.user).update();
		// 셋 가운데 하나만 미국 — 사건의 지역이 아니라 큰 슬롯 사건이고, 미국 기사도 표본에 든다
		region(conflict[0], "us");

		DigestInput oneThird = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(oneThird)).first().isEqualTo(event(conflict[0]) + ":MAJOR_STORYLINE");
		assertThat(articlesOf(oneThird, event(conflict[0]))).containsExactly(conflict[0], conflict[1], conflict[2]);

		// 둘이 미국 — 사건의 지역이 제외라 큰 슬롯이 없다
		region(conflict[1], "us");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void anArticleWithoutAnEvaluationIsNotInTheBigEventSample() {
		// 평가 없는 기사가 사건에 함께 배정됐다 — 지역 · 형식 제외를 확인할 수 없어 표본에 들지 않는다(digest-R19). 중요도가 없어 중요도
		// 합 · 소스는 그대로라 보도 강도도 그대로다
		long unevaluated = EventArticles.insert(this.jdbc, "분쟁 영", "요약", 1, 11);
		long[] conflict = bigStoryline(unevaluated);

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(articlesOf(input, event(conflict[0]))).containsExactly(conflict[0], conflict[1], conflict[2]);
		assertThat(articles(input)).doesNotContain(unevaluated);
	}

	@Test
	void anUnknownAccountHasNoInput() {
		assertThat(this.selection.select(new UserId(Long.MAX_VALUE), DAY, ROOMY)).isEmpty();
	}

	@Test
	void aMismatchedActiveVersionIsRetriedOnceAndThenRefused() {
		TagSetId elsewhere = new TagSetId(this.tagSets.activeTagSet().id().value() + 1_000);
		AtomicInteger skewed = new AtomicInteger(1);
		CandidateSelection once = new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, skewing(skewed, elsewhere)), this.tagSets, this.articles, this.events,
				this.storylines, this.interestMaterials, this.feedback, InstantSource.system());

		assertThat(events(once.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
		assertThat(skewed).hasValue(0);

		AtomicInteger always = new AtomicInteger(Integer.MAX_VALUE);
		CandidateSelection never = new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, skewing(always, elsewhere)), this.tagSets, this.articles, this.events,
				this.storylines, this.interestMaterials, this.feedback, InstantSource.system());
		assertThatIllegalStateException().isThrownBy(() -> never.select(new UserId(this.user), DAY, ROOMY))
			.withMessageContaining("2 번 어긋났다");
	}

	@Test
	void anEventOnATopicTheUserLikedEntersByInterest() {
		// 입법 3.6 은 중립이면 E1(4.0) 뒤라 사건 하나의 예산 밖이다. 지난 다이제스트가 입법 사건 일곱을 보였고 모두 좋아요 —
		// 입법 가중치 ≈ 1 + 0.5 × 7 ÷ 10 ≈ 1.35 라 3.6 × 1.35 ≈ 4.8 이 4.0 앞이다
		long legislature = event(eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1));
		long[] shown = likedLegislatureShown(7);

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(legislature + ":INTEREST");

		// 대조 — 좋아요를 모두 없음으로 되돌리면 중립 순서 그대로
		for (long event : shown) {
			react(event, "NONE", "2026-11-04T08:00:00Z");
		}
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");
	}

	@Test
	void aSubscribedStorylineDoesNotRaiseTheTopicsOfItsEvents() {
		// 입법 기사 하나뿐인 지난 사건의 스토리라인을 구독했다 — 스토리라인 구독은 그 스토리라인의 꼭대기 관심에만 들고 주제(입법)에는 들지 않는다.
		// 후보 "의회 넷이"(3.6)는 그 스토리라인의 소속이 아니라 중립 순서 그대로 E1(4.0)이 사건 하나의 예산을 쓴다
		eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
		subscribeStoryline(legislatureStoryline(), "USER");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");
	}

	@Test
	void aCandidateInASubscribedStorylineRisesByItsStorylineWeight() {
		// 후보 "의회 넷이"(3.6)를 구독한 스토리라인의 소속으로 더한다 — 꼭대기 관심 γ 1/3 × 1 = 1/3, 가중치 1 + 0.5 × 1/3 = 1.1666… 이라
		// 3.6 × 1.1666… = 4.2 가 E1(4.0) 앞이고, 중립 순서로는 들지 못하므로 INTEREST
		long legislature = event(eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1));
		long storyline = legislatureStoryline();
		subscribeStoryline(storyline, "USER");
		// 대조 — 소속이 아니면 주제에 들지 않아 중립 순서 그대로
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");

		this.rows.member(storyline, legislature);

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(legislature + ":INTEREST");
	}

	@Test
	void aStorylineDislikedTheDayBeforeLeavesItsNewEventAndItsBigRowOut() {
		long[] conflict = bigStoryline();
		long bigEvent = event(conflict[0]);
		long storyline = storylineOf(bigEvent);
		// 같은 스토리라인의 새 사건 — 구독한 정치 행정, 기간 안(큰 판정 행의 사건이라 큰 슬롯으로 든다)
		long fresh = event(eventArticle("분쟁 속 정부 대응", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1));
		this.rows.member(storyline, fresh);
		// 그 스토리라인의 지난 사건 — 11-04 다이제스트가 혼자인 항목으로 보였다(몫 1)
		long past = pastMemberOf(storyline, "분쟁 지난 일");
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z"), past);
		// 대조 — 싫어요 전에는 그 스토리라인의 두 사건이 큰 슬롯으로 든다
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY))).containsExactly(
				bigEvent + ":MAJOR_STORYLINE", fresh + ":MAJOR_STORYLINE", this.e1 + ":SUBSCRIBED",
				event(this.a5) + ":SUBSCRIBED");

		// 지난 사건에 싫어요 — 꼭대기 관심 −1 ÷ (1 + 3) = −0.25 ≤ −0.2 라 거른 꼭대기다. 큰 판정 행을 건너뛰고(다음 행이 없다), 새 사건은
		// 꼭대기가 모두 거른 꼭대기라 구독 사건으로도 들지 않는다
		react(past, "DISLIKE", "2026-11-04T07:00:00Z");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void aDislikeAfterASilentExposureOfTheSameFlowLeavesItsBigRowOut() {
		long[] conflict = bigStoryline();
		long storyline = storylineOf(event(conflict[0]));
		// 그 스토리라인의 지난 사건 a · b — 11-03 다이제스트가 a 를 혼자인 항목으로 보였고 반응이 없었다. 11-04 다이제스트가 b 를 혼자인 항목으로
		// 보였고 싫어요. 스토리라인 묶음은 b 의 싫어요가 앞선 무반응 a 를 대신한다 — x ≈ −0.96 ÷ (0.96 + 3) ≈ −0.242 ≤ −0.2 라 행을
		// 건너뛴다(대신하지 않으면 a 가 분모에 남아 ≈ −0.196 으로 들어온다)
		long a = pastMemberOf(storyline, "분쟁 그제 일");
		long b = pastMemberOf(storyline, "분쟁 어제 일");
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, this.user, "2026-11-03", "2026-11-03T06:00:00Z"), a);
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z"), b);
		react(b, "DISLIKE", "2026-11-04T07:00:00Z");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");

		// 대조 — b 를 좋아요로 바꾸면 행이 든다
		react(b, "LIKE", "2026-11-04T08:00:00Z");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY))).first()
			.isEqualTo(event(conflict[0]) + ":MAJOR_STORYLINE");
	}

	@Test
	void anEventOfASubscribedStorylineOutsideTheSubscribedTopicsEntersByInterest() {
		// 구독 밖(국제 분쟁) 기사 하나뿐인 사건과 그 스토리라인 — 보도 강도가 작아 크지 않다
		long article = article("분쟁 소식", "world.conflict", 3.0, "2026-11-05T06:00:00Z", 1);
		long flowEvent = this.rows.event(1, "2026-11-05T06:00:00Z", "2026-11-05T06:00:00Z");
		this.rows.assign(article, flowEvent, "2026-11-05T06:00:00Z");
		long storyline = this.rows.storyline("2026-11-05T06:00:00Z", "2026-11-05T06:00:00Z", flowEvent);
		this.rows.member(storyline, flowEvent);
		// 대조 — 구독 전에는 구독 주제 밖이라 들지 않는다
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");

		subscribeStoryline(storyline, "USER");

		// 구독한 스토리라인은 관심 흐름 — 그 사건이 새 길로 들고 표본 기사가 입력에 실린다
		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();
		assertThat(events(input)).contains(flowEvent + ":INTEREST");
		assertThat(articlesOf(input, flowEvent)).containsExactly(article);
	}

	@Test
	void aDislikedEarlierFlowLeavesTheNewEventOfItsLaterFlowOut() {
		// 앞 흐름 — 지난 사건 하나(11-04 다이제스트가 혼자인 항목으로 보였다)에 싫어요: 그 흐름의 관심 −1 ÷ 4 = −0.25 ≤ −0.2
		long earlier = shownAndDislikedStoryline();
		// 뒤 흐름 — 구독한 정치 행정의 새 사건 하나뿐인 다른 스토리라인
		long fresh = event(eventArticle("정부 새 대응", "politics.executive", 3.5, "2026-11-05T06:00:00Z", 1));
		long later = this.rows.storyline("2026-11-05T06:00:00Z", "2026-11-05T06:00:00Z", fresh);
		this.rows.member(later, fresh);
		// 대조 — 이어짐이 없으면 다른 흐름이라 새 사건이 구독 사건으로 든다
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY))).contains(fresh + ":SUBSCRIBED");

		UserStorylineFixtures.follows(this.jdbc, earlier, later);

		// 사용자 스토리라인이 뒤 흐름을 따라가 같은 관심을 쓴다 — 새 사건의 꼭대기가 거른 흐름이라 빠진다
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void anEventOfTheLaterFlowOfATrackedFlowOutsideTheSubscribedTopicsEntersByInterest() {
		long tracked = legislatureStoryline();
		subscribeStoryline(tracked, "USER");
		// 뒤 흐름 — 구독 밖(국제 분쟁) 기사 하나뿐인 사건과 그 스토리라인, 보도 강도가 작아 크지 않다
		long article = article("분쟁 소식", "world.conflict", 3.0, "2026-11-05T06:00:00Z", 1);
		long flowEvent = this.rows.event(1, "2026-11-05T06:00:00Z", "2026-11-05T06:00:00Z");
		this.rows.assign(article, flowEvent, "2026-11-05T06:00:00Z");
		long later = this.rows.storyline("2026-11-05T06:00:00Z", "2026-11-05T06:00:00Z", flowEvent);
		this.rows.member(later, flowEvent);
		// 대조 — 이어짐이 없으면 추적하지 않는 흐름이고 구독 주제 밖이라 들지 않는다
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");

		UserStorylineFixtures.follows(this.jdbc, tracked, later);

		// 추적한 흐름이 이어진 뒤 흐름도 관심 흐름이다 — 저장한 추적 대상에 아직 없어도(읽을 때 계산) 새 길로 든다
		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();
		assertThat(events(input)).contains(flowEvent + ":INTEREST");
		assertThat(articlesOf(input, flowEvent)).containsExactly(article);
	}

	@Test
	void aBigRowWhoseTopIsTheLaterFlowOfADislikedFlowIsSkipped() {
		long bigEvent = event(bigStoryline()[0]);
		long big = storylineOf(bigEvent);
		long earlier = shownAndDislikedStoryline();
		// 대조 — 이어짐이 없으면 큰 슬롯으로 든다
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY))).containsExactly(
				bigEvent + ":MAJOR_STORYLINE", this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");

		UserStorylineFixtures.follows(this.jdbc, earlier, big);

		// 큰 판정 행 머리의 꼭대기가 거른 흐름의 뒤 흐름이라 건너뛴다(다음 행이 없다)
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void aStorylineSubscriptionRewrittenByAMergeIsNotASignal() {
		eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
		subscribeStoryline(legislatureStoryline(), "STORYLINE_MERGED");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");
	}

	@Test
	void aDislikedEventLeavesTheSubscribedCandidatesWithAllItsArticles() {
		// 1 · 2 는 같은 사건 E1 — 사건에 싫어요를 누르면 사건이 빠지고 그 사건의 어느 기사도 표본에 들지 않는다
		press(this.e1, "DISLIKE");

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(input)).containsExactly(event(this.a5) + ":SUBSCRIBED");
		assertThat(articles(input)).doesNotContain(this.a1, this.a2);
	}

	@Test
	void aLikedEventStaysWithItsArticles() {
		// 대조 — 싫어요를 좋아요로 바꾸면 사건과 그 기사가 다시 든다
		press(this.e1, "DISLIKE");
		press(this.e1, "LIKE");

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(input)).containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
		assertThat(articlesOf(input, this.e1)).containsExactly(this.a1, this.a2);
	}

	@Test
	void aDislikeOnAnEventMergedIntoACandidateLeavesTheCandidateOut() {
		// 싫어요를 누른 사건 M 이 뒤에 E1 에 합쳐졌다 — 남는 사건 E1 이 빠진다
		long merged = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
		StorylineRows.merge(this.jdbc, merged, this.e1);
		press(merged, "DISLIKE");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(event(this.a5) + ":SUBSCRIBED");
	}

	@Test
	void aDislikedBigEventLeavesTheBigSlot() {
		long[] conflict = bigStoryline();
		press(event(conflict[0]), "DISLIKE");

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(events(input)).doesNotContain(event(conflict[0]) + ":MAJOR_STORYLINE");
		assertThat(articles(input)).doesNotContain(conflict[0], conflict[1], conflict[2]);
	}

	@Test
	void aLikedBigEventKeepsTheBigSlot() {
		// 대조 — 좋아요로 바꾸면 큰 슬롯에 든다
		long[] conflict = bigStoryline();
		press(event(conflict[0]), "DISLIKE");
		press(event(conflict[0]), "LIKE");

		assertThat(articlesOf(this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow(),
				event(conflict[0])))
			.containsExactly(conflict[0], conflict[1], conflict[2]);
	}

	@Test
	void aBigEventOnATopicTheUserDislikesLeavesTheBigSlotToTheNextRow() {
		// 큰 슬롯 1 · 큰 판정 행 둘 — 1위 국내 재판(기사 넷 · 중요도 5 → 보도 강도 20), 2위 국제 분쟁(셋 → 15). 15 < 20 × 0.8 이라 순서만으로는
		// 뒤집히지 않는다(재판 가중치 0.8 이면 16 > 15)
		this.jdbc.sql("update user_settings set big_slots = 1 where user_id = :user").param("user", this.user).update();
		long trial = bigRow("society.law", 5.0, 5.0, 5.0, 5.0)[0];
		long conflict = event(bigStoryline()[0]);
		// 대조 ⑴ — 싫어요 전에는 1위 행
		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY))).first()
			.isEqualTo(trial + ":MAJOR_STORYLINE");

		// 지난 다이제스트(11-04)의 혼자인 재판 항목 둘에 싫어요 — 재판 관심 ≈ −2 ÷ (2 + 3) = −0.4(가중치 ≈ 0.8). 그 지난 사건들은 스토리라인
		// 소속이 없어 흐름 거름이 아니라 주제 거름으로 빠진다
		dislikedShown("society.law", 2);

		List<String> filtered = events(this.selection.select(new UserId(this.user), DAY, ROOMY));
		assertThat(filtered).first().isEqualTo(conflict + ":MAJOR_STORYLINE");
		assertThat(filtered).doesNotContain(trial + ":MAJOR_STORYLINE");

		// 대조 ⑵ — 재판을 구독하면(구독 항 γ 0.1 을 더해도 ≈ −0.3) 같은 사건이 큰 슬롯이 아니라 구독 사건으로 든다(주제 거름은 큰 슬롯에만)
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', 'society.law', 'SUBSCRIBE', now())
				""").param("user", this.user).update();
		topicChange("society.law", "ADD");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ROOMY)))
			.contains(conflict + ":MAJOR_STORYLINE", trial + ":SUBSCRIBED");
	}

	@Test
	void theBigRowOrderMultipliesTheInterestAndTheItemCarriesThatRowsTop() {
		// 큰 슬롯 1 · 큰 판정 행 둘 — 1위 국제 분쟁(중요도 5 · 5 · 5 · 4 → 19), 2위 입법(셋 → 15)
		this.jdbc.sql("update user_settings set big_slots = 1 where user_id = :user").param("user", this.user).update();
		long[] conflict = bigRow("world.conflict", 5.0, 5.0, 5.0, 4.0);
		long[] legislature = bigRow("politics.legislature", 5.0, 5.0, 5.0);
		// 대조 — 관심 신호가 없으면 1위 행 · 그 행의 꼭대기
		DigestInput neutral = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();
		assertThat(events(neutral)).first().isEqualTo(conflict[0] + ":MAJOR_STORYLINE");
		assertThat(neutral.items().getFirst().storyline()).isEqualTo(new StorylineId(conflict[1]));

		// 입법 사건 일곱에 좋아요 — 입법 가중치 ≈ 1.35, 15 × 1.35 ≈ 20.2 > 19
		likedLegislatureShown(7);

		DigestInput liked = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();
		assertThat(events(liked)).first().isEqualTo(legislature[0] + ":MAJOR_STORYLINE");
		assertThat(events(liked)).doesNotContain(conflict[0] + ":MAJOR_STORYLINE");
		assertThat(liked.items().getFirst().storyline()).isEqualTo(new StorylineId(legislature[1]));
	}

	@Test
	void signalsAfterThePeriodEndAreReadUpToNow() {
		// 지난 다이제스트(11-04)가 보인 입법 사건 일곱에 좋아요를 기간 끝 11-06 0시 뒤 12시에 눌렀다 — 지금이 그 뒤면 읽혀 무게 1 로 세고
		// (입법 가중치 ≈ 1 + 0.5 × 7 ÷ 10 ≈ 1.35, 3.6 × 1.35 ≈ 4.8 이 4.0 앞), 지금이 그 앞이면 읽는 범위 밖이라 중립 순서다
		long legislature = event(eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1));
		likedLegislatureShown(7, "2026-11-06T12:00:00Z");

		assertThat(events(selectionAt("2026-11-07T00:00:00Z").select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(legislature + ":INTEREST");
		assertThat(events(selectionAt("2026-11-06T06:00:00Z").select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");
	}

	/** 시계를 고정한 후보 선별 */
	private CandidateSelection selectionAt(String now) {
		return new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, this.lineage), this.tagSets, this.articles, this.events,
				this.storylines, this.interestMaterials, this.feedback,
				InstantSource.fixed(Instant.parse(now)));
	}

	@Test
	void theNeutralResultIsCutByTheSameBudgetWithItsOwnEvents() {
		// 사건마다 기사 하나(127). 최대 후보 10 · 예산 9 개. 북한(D 4.8)은 뺌 넷으로 가중치 0.5, 외교(Z 4.55)는 더함 넷으로 1.5.
		// 관심도 결과 10: Z · n1 ~ n4 · n6 ~ n9 · n11 → 예산 9: Z · n1 ~ n4 · n6 ~ n9
		// 중립 결과 10: n1 ~ n4 · D · n6 ~ n9 · Z → 예산 9: n1 ~ n4 · D · n6 ~ n9 — Z 는 중립으로는 들지 못해 INTEREST.
		// 중립에만 든 D 의 토큰을 읽지 않으면 중립 결과가 D 를 버리고 Z 를 담아 Z 가 SUBSCRIBED 가 된다
		this.jdbc
			.sql("update user_settings set candidate_volume = 'CUSTOM', candidate_count = 10 where user_id = :user")
			.param("user", this.user)
			.update();
		long[] n = new long[12];
		double[] importance = { 0, 5.0, 4.95, 4.9, 4.85, 0, 4.75, 4.7, 4.65, 4.6, 0, 4.5 };
		for (int i : new int[] { 1, 2, 3, 4, 6, 7, 8, 9, 11 }) {
			n[i] = eventArticle("기사 %02d".formatted(i), "politics.executive", importance[i], "2026-11-05T06:00:00Z", 1);
		}
		long d = eventArticle("기사 05", "politics.north-korea", 4.8, "2026-11-05T06:00:00Z", 1);
		long z = eventArticle("기사 10", "politics.diplomacy-defense", 4.55, "2026-11-05T06:00:00Z", 1);
		for (int i = 0; i < 4; i++) {
			topicChange("politics.north-korea", "REMOVE");
			topicChange("politics.diplomacy-defense", "ADD");
		}
		LlmModel nine = new LlmModel(Provider.OPENAI, "gpt-test", 6_000 + 20 + 200 + 9 * 127, true);

		DigestInput input = this.selection.select(new UserId(this.user), DAY, nine).orElseThrow();

		// 사건마다 기사 하나라 기사 순서가 항목 순서다 — 주요 뉴스 다섯 다음 헤드라인 넷
		assertThat(articles(input)).containsExactly(z, n[1], n[2], n[3], n[4], n[6], n[7], n[8], n[9]);
		assertThat(events(input)).filteredOn(entry -> entry.endsWith(":INTEREST")).containsExactly(event(z) + ":INTEREST");
		assertThat(articles(input)).doesNotContain(d);
		assertThat(input.items()).extracting(InputItem::kind)
			.containsExactly(ItemKind.TOP_STORY, ItemKind.TOP_STORY, ItemKind.TOP_STORY, ItemKind.TOP_STORY,
					ItemKind.TOP_STORY, ItemKind.HEADLINE, ItemKind.HEADLINE, ItemKind.HEADLINE, ItemKind.HEADLINE);
	}

	@Test
	void aMismatchedActiveVersionOfTheInterestMaterialsIsRetriedOnceAndThenRefused() {
		likedLegislatureShown(1);
		TagSetId elsewhere = new TagSetId(this.tagSets.activeTagSet().id().value() + 1_000);
		AtomicInteger skewed = new AtomicInteger(1);
		CandidateSelection once = selectionWithInterestLineage(skewing(skewed, elsewhere));

		assertThat(events(once.select(new UserId(this.user), DAY, ROOMY)))
			.containsExactly(this.e1 + ":SUBSCRIBED", event(this.a5) + ":SUBSCRIBED");
		assertThat(skewed).hasValue(0);

		CandidateSelection never = selectionWithInterestLineage(
				skewing(new AtomicInteger(Integer.MAX_VALUE), elsewhere));
		assertThatIllegalStateException().isThrownBy(() -> never.select(new UserId(this.user), DAY, ROOMY))
			.withMessageContaining("2 번 어긋났다");
	}

	/** 관심도 재료 쪽에만 계보 표를 넣은 후보 선별 */
	private CandidateSelection selectionWithInterestLineage(TagKeyLineage interestLineage) {
		InterestMaterials materials = new InterestMaterials(this.exposures, this.feedback,
				new ActiveEvaluations(this.evaluations, interestLineage), this.topicHistory,
				this.storylineSubscriptions, this.storylines, this.events, this.userStorylines);
		return new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, this.lineage), this.tagSets, this.articles, this.events,
				this.storylines, materials, this.feedback, InstantSource.system());
	}

	/**
	 * 지난 다이제스트(11-04 06시에 끝냄)가 입법 사건 여럿을 저마다 사건 하나인 항목으로 보였고 사용자가 모두 좋아요를 눌렀다 — 사건마다 입법
	 * 기사 하나(기간 앞 11-01 에 평가 · 배정)라 후보가 아니다. 몫은 저마다 1 이다
	 * @return 보인 사건
	 */
	private long[] likedLegislatureShown(int count) {
		return likedLegislatureShown(count, "2026-11-04T07:00:00Z");
	}

	/** {@link #likedLegislatureShown(int)} 의 좋아요를 {@code reactedAt} 에 누른 것 */
	private long[] likedLegislatureShown(int count, String reactedAt) {
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z");
		long[] shown = new long[count];
		for (int i = 0; i < count; i++) {
			long article = article("의회 과거 " + i, "politics.legislature", 3.0, "2026-11-01T00:00:00Z", 1);
			shown[i] = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
			this.rows.assign(article, shown[i], "2026-11-01T00:00:00Z");
			DigestRows.item(this.jdbc, digest, shown[i]);
			react(shown[i], "LIKE", reactedAt);
		}
		return shown;
	}

	/** 사건이 직접 든 스토리라인 하나 */
	private long storylineOf(long event) {
		return this.jdbc.sql("select storyline_id from storyline_members where event_id = :event")
			.param("event", event)
			.query(Long.class)
			.single();
	}

	/** 기간 앞(11-01)의 입법 기사 하나를 가진 사건과 그 사건만의 스토리라인 — 보도 강도가 작아 크지 않다 */
	private long legislatureStoryline() {
		long article = article("의회 지난 일", "politics.legislature", 3.0, "2026-11-01T00:00:00Z", 1);
		long event = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
		this.rows.assign(article, event, "2026-11-01T00:00:00Z");
		long storyline = this.rows.storyline("2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", event);
		this.rows.member(storyline, event);
		return storyline;
	}

	/**
	 * 구독 밖(국제 정치)의 지난 사건(11-01) 하나뿐인 스토리라인 — 11-04 다이제스트가 그 사건을 혼자인 항목으로 보였고(몫 1) 사용자가 싫어요를
	 * 눌렀다. 보도 강도가 작아 크지 않다. 주제는 {@link #bigStoryline} 의 국제 분쟁과 다르다 — 싫어요가 큰 판정 사건의 주제 관심을 거름
	 * 기준까지 내려 흐름 거름과 주제 거름이 섞이지 않게
	 */
	private long shownAndDislikedStoryline() {
		long article = article("국제 정치 지난 일", "world.politics", 3.0, "2026-11-01T00:00:00Z", 1);
		long past = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
		this.rows.assign(article, past, "2026-11-01T00:00:00Z");
		long storyline = this.rows.storyline("2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z", past);
		this.rows.member(storyline, past);
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z"), past);
		react(past, "DISLIKE", "2026-11-04T07:00:00Z");
		return storyline;
	}

	private void subscribeStoryline(long storyline, String cause) {
		subscribeStoryline(storyline, cause, "2026-11-05T12:00:00Z");
	}

	/** 스토리라인 구독 — 이력 한 줄(더함)과 추적 중인 사용자 스토리라인 하나(추적 대상 · 추적 씨앗이 그 스토리라인) */
	private void subscribeStoryline(long storyline, String cause, String at) {
		this.jdbc.sql("""
				insert into user_storyline_subscription_events (user_id, storyline_id, action, cause, occurred_at)
				values (:user, :storyline, 'ADD', :cause, cast(:at as timestamptz))
				""")
			.param("user", this.user)
			.param("storyline", storyline)
			.param("cause", cause)
			.param("at", at)
			.update();
		long userStoryline = this.jdbc.sql("""
				insert into user_storylines (user_id, created_at) values (:user, cast(:at as timestamptz)) returning id
				""").param("user", this.user).param("at", at).query(Long.class).single();
		this.jdbc.sql("insert into user_storyline_members (user_storyline_id, storyline_id) values (:id, :storyline)")
			.param("id", userStoryline)
			.param("storyline", storyline)
			.update();
		this.jdbc.sql("insert into user_storyline_tracked_seeds (user_storyline_id, storyline_id) values (:id, :storyline)")
			.param("id", userStoryline)
			.param("storyline", storyline)
			.update();
		this.jdbc.sql("""
				insert into user_storyline_subscriptions (user_id, user_storyline_id, created_at)
				values (:user, :id, cast(:at as timestamptz))
				""").param("user", this.user).param("id", userStoryline).param("at", at).update();
	}

	private void topicChange(String key, String action) {
		this.jdbc.sql("""
				insert into user_tag_preference_events (user_id, cause, tag_set_id, tag_kind, tag_key, mode, action,
				  occurred_at)
				values (:user, 'USER', :tagSet, 'TOPIC', :key, 'SUBSCRIBE', :action, '2026-11-05T12:00:00Z')
				""")
			.param("user", this.user)
			.param("tagSet", this.tagSets.activeTagSet().id().value())
			.param("key", key)
			.param("action", action)
			.update();
	}

	/** 사건 하나의 이력 한 줄 — 관심도의 반응 */
	private void react(long event, String value, String at) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, event_id, value, digest_id, occurred_at)
				values (:user, :event, :value, 1, cast(:at as timestamptz))
				""").param("user", this.user).param("event", event).param("value", value).param("at", at).update();
	}

	/** 사건 하나에 누른 값 — 이력 한 줄을 더하고 현재 상태를 그 값으로 둔다 */
	private void press(long event, String value) {
		react(event, value, "2026-11-04T07:00:00Z");
		this.jdbc.sql("""
				insert into feedback (user_id, event_id, value, digest_id, updated_at)
				values (:user, :event, :value, 1, now())
				on conflict (user_id, event_id) do update set value = excluded.value
				""").param("user", this.user).param("event", event).param("value", value).update();
	}

	/**
	 * 구독 밖(국제 분쟁) 사건 하나와 그것만 가진 스토리라인 — 기사 셋 · 중요도 5 · 모두 소스 1. 기간의 소스는 소스 1 하나(사건에 배정된 기사의
	 * 소스)라 보도 강도 = 15 ÷ (1 × 1일) = 15 — 큰 판정 기준의 처음 값 14.00 이상이라 크다
	 * @param unevaluated 사건에 함께 배정할 평가 없는 기사
	 * @return 평가 있는 기사 ID — ID 순
	 */
	private long[] bigStoryline(long... unevaluated) {
		long b1 = article("분쟁 하나", "world.conflict", 5.0, "2026-11-05T06:00:00Z", 1);
		long b2 = article("분쟁 둘", "world.conflict", 5.0, "2026-11-05T06:00:00Z", 1);
		long b3 = article("분쟁 셋", "world.conflict", 5.0, "2026-11-05T06:00:00Z", 1);
		long event = this.rows.event(3, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		this.rows.assign(b1, event, "2026-11-05T00:00:00Z");
		this.rows.assign(b2, event, "2026-11-05T00:00:00Z");
		this.rows.assign(b3, event, "2026-11-05T00:00:00Z");
		for (long article : unevaluated) {
			this.rows.assign(article, event, "2026-11-05T00:00:00Z");
		}
		this.soloEvents.put(b1, event);
		long storyline = this.rows.storyline("2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z", event);
		this.rows.member(storyline, event);
		return new long[] { b1, b2, b3 };
	}

	/**
	 * 큰 판정 행 하나 — 주제가 같은 기사들(모두 소스 1 · 기간 안 11-05)을 가진 사건 하나와 그것만 가진 스토리라인. 보도 강도 = 중요도 합 ÷ (1 × 1일)
	 * @return 사건 ID · 스토리라인 ID
	 */
	private long[] bigRow(String topic, double... importances) {
		long event = this.rows.event(importances.length, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		for (int i = 0; i < importances.length; i++) {
			long article = article(topic + " " + i, topic, importances[i], "2026-11-05T06:00:00Z", 1);
			this.rows.assign(article, event, "2026-11-05T00:00:00Z");
		}
		long storyline = this.rows.storyline("2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z", event);
		this.rows.member(storyline, event);
		return new long[] { event, storyline };
	}

	/** 11-04 다이제스트가 그 주제의 지난 사건(11-01 · 스토리라인 없음)을 혼자인 항목으로 {@code count} 개 보였고 모두 싫어요 */
	private void dislikedShown(String topic, int count) {
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z");
		for (int i = 0; i < count; i++) {
			long article = article(topic + " 과거 " + i, topic, 3.0, "2026-11-01T00:00:00Z", 1);
			long past = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
			this.rows.assign(article, past, "2026-11-01T00:00:00Z");
			DigestRows.item(this.jdbc, digest, past);
			react(past, "DISLIKE", "2026-11-04T07:00:00Z");
		}
	}

	/** 기간 앞(11-01)의 구독 밖(국제 분쟁) 기사 하나를 가진 사건을 그 스토리라인의 소속으로 — 사건 ID */
	private long pastMemberOf(long storyline, String title) {
		long article = article(title, "world.conflict", 3.0, "2026-11-01T00:00:00Z", 1);
		long event = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
		this.rows.assign(article, event, "2026-11-01T00:00:00Z");
		this.rows.member(storyline, event);
		return event;
	}

	/** 입력의 사건마다 "사건 ID:선정 이유" — 항목 순서, 항목 안 사건 순서. 계정이 있어야 한다 */
	private static List<String> events(Optional<DigestInput> input) {
		return events(input.orElseThrow());
	}

	private static List<String> events(DigestInput input) {
		return input.items()
			.stream()
			.flatMap(item -> item.events().stream())
			.map(event -> event.event().value() + ":" + event.reason())
			.toList();
	}

	/** 입력의 표본 기사 — 항목 · 사건 · 기사 순서 */
	private static List<Long> articles(DigestInput input) {
		return input.items()
			.stream()
			.flatMap(item -> item.events().stream())
			.flatMap(event -> event.articles().stream())
			.map(article -> article.candidate().articleId().value())
			.toList();
	}

	/** 그 사건의 표본 기사 — 입력에 그 사건이 없으면 빈 목록 */
	private static List<Long> articlesOf(DigestInput input, long event) {
		return input.items()
			.stream()
			.flatMap(item -> item.events().stream())
			.filter(entry -> entry.event().value() == event)
			.flatMap(entry -> entry.articles().stream())
			.map(article -> article.candidate().articleId().value())
			.toList();
	}

	/** 시험이 그 기사로 만든 사건 */
	private long event(long article) {
		return Objects.requireNonNull(this.soloEvents.get(article), "시험이 만든 사건이 없는 기사다");
	}

	/** 기사들을 한 사건에 배정한다 — 배정의 발행 시각은 기간 첫 시각 */
	private long eventOf(long... articles) {
		long event = this.rows.event(articles.length, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		for (long article : articles) {
			this.rows.assign(article, event, "2026-11-05T00:00:00Z");
		}
		this.soloEvents.put(articles[0], event);
		return event;
	}

	/** 기사와 그 기사만 가진 사건 */
	private long eventArticle(String title, String topic, double importance, String evaluatedAt, long source) {
		long article = article(title, topic, importance, evaluatedAt, source);
		eventOf(article);
		return article;
	}

	/** 대분류를 하나 더 구독한다 — 구독 변경 이력은 남기지 않는다 */
	private void subscribeTopic(String key) {
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', :key, 'SUBSCRIBE', now())
				""").param("user", this.user).param("key", key).update();
	}

	/** 기사의 평가에 지역을 붙인다 */
	private long region(long article, String region) {
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				select id, article_id, 'REGION', :region, 0.9 from article_evaluations where article_id = :article
				""").param("region", region).param("article", article).update();
		return article;
	}

	/** 남은 횟수만큼 계보 표의 활성 버전을 다른 값으로 바꿔 낸다 — 조회 사이의 활성 전환 */
	private TagKeyLineage skewing(AtomicInteger remaining, TagSetId elsewhere) {
		return from -> {
			TagKeyMapping real = this.lineage.toActive(from);
			if (remaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
				return new TagKeyMapping(real.from(), elsewhere, real.keys());
			}
			return real;
		};
	}

	/** 정치 대분류를 구독하고 소스 2 를 제외한 사용자 — 설정 행은 기본값 */
	private long user() {
		long id = this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('selection@example.com', '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, interest_text, weekly_report,
				    weekly_report_day, monthly_report, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', '', true, 1, true, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", id).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', 'politics', 'SUBSCRIBE', now())
				""").param("user", id).update();
		this.jdbc.sql("insert into user_excluded_sources (user_id, source_id, created_at) values (:user, 2, now())")
			.param("user", id)
			.update();
		return id;
	}

	/** 기사 · 엔트리(소스 하나)와 그 평가 — 주제 하나(확률 0.9) · 대표 형식 straight · 중요도 */
	private long article(String title, String topic, double importance, String evaluatedAt, long source) {
		long article = EventArticles.insert(this.jdbc, title, "요약", source, 10 + source);
		long evaluation = this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
				    format_distribution, importance, importance_distribution, evaluated_at)
				values (:article, :tagSet, :article, 'straight', 1.0, '{}', :importance, '{}', :at)
				returning id
				""")
			.param("article", article)
			.param("tagSet", this.tagSets.activeTagSet().id().value())
			.param("importance", importance)
			.param("at", Instant.parse(evaluatedAt).atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				values (:evaluation, :article, 'TOPIC', :topic, 0.9)
				""").param("evaluation", evaluation).param("article", article).param("topic", topic).update();
		return article;
	}

}
