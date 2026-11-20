package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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
import net.yozmnews.digest.DigestRows;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.InputEvent;
import net.yozmnews.digest.domain.input.InputItem;
import net.yozmnews.digest.domain.item.ItemKind;
import net.yozmnews.event.EventId;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.SummaryLength;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.TopicSubscriptionHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.jobrunr.scheduling.JobScheduler;
import net.yozmnews.digest.DigestRounds;

/**
 * 후보 선별의 재료 경로 — 실제 PostgreSQL 의 사용자 설정 · 기사 · 평가 · 사건 · 스토리라인 행에서 관심 설정 조회 · 첫 평가 시각 · 평가 · 발행
 * 시각 · 소스 · 사건 · 사건의 소속 기사 평가 · 꼭대기 · 내용 조회를 거쳐 규칙까지. 기본 체계(버전 1)로 본다. 기간은 2026-11-05 하루(UTC)이고
 * 기사는 모두 그날 0시 발행이다({@link EventArticles#insert}).
 * <p>
 * 지키는 규칙: digest-R15 ~ digest-R19 · digest-R21 · digest-R22 · digest-R25 · digest-R44 ~ digest-R46 — 후보는 사건(사건 없는 기사는 버림), 구독 필터는 사건의 주제 · 지역 제외는 사건의 지역 · 기사 제외는 기사마다, 사건 중요도는 소속
 * 기사의 최대, 표본(기간에 든 · 제외를 지난 기사), 상한 · 선정 이유, 항목 묶기(꼭대기), 관심 설정과 계보 표의 활성 버전 대조, 관심도 순위와
 * {@code INTEREST}(노출 · 좋아요 · 주제 구독 변경 · 스토리라인 구독 변경), 싫어요 거름(구독 사건 · 큰 슬롯 사건 — 합쳐진 사건의 싫어요 포함), 중립
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

	private ReaderRows rows;

	/** 기사 → 그 기사만 가진 사건(시험이 만든 것) */
	private final Map<Long, Long> soloEvents = new HashMap<>();

	private long user;

	private long a1;

	private long a2;

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
		eventArticle("시장", "economy.markets", 5.0, "2026-11-05T06:00:00Z", 1);
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
	void aSubscribedStorylineRaisesTheTopicsOfItsEvents() {
		// 입법 기사 하나뿐인 사건의 스토리라인을 구독했다 — 입법 몫 1, 가중치 ≈ 1 + 0.5 × 1 ÷ 3 ≈ 1.17 이라 3.6 × 1.17 ≈ 4.2 가 4.0 앞이다
		long legislature = event(eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1));
		subscribeStoryline(legislatureStoryline(), "USER");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(legislature + ":INTEREST");
	}

	@Test
	void aSubscribedStorylineCountsTheTopicOfEachEventByItsMembersNotByItsRepresentative() {
		// 지난 사건의 대표 기사는 경제, 나머지 소속 둘은 입법 — 사건의 주제는 입법(2/3)이라 입법 몫 1, 가중치 ≈ 1.17 이고
		// 3.6 × 1.17 ≈ 4.2 가 4.0 앞이다. 대표 기사로 세면 경제 몫 1 이라 입법 사건은 4.0 뒤다
		long legislature = event(eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1));
		long representative = article("지난 경제", "economy.markets", 3.0, "2026-11-01T00:00:00Z", 1);
		long event = this.rows.event(3, "2026-11-01T00:00:00Z", "2026-11-01T02:00:00Z");
		this.rows.assign(representative, event, "2026-11-01T00:00:00Z");
		this.rows.assign(article("지난 의회 하나", "politics.legislature", 3.0, "2026-11-01T01:00:00Z", 2), event,
				"2026-11-01T01:00:00Z");
		this.rows.assign(article("지난 의회 둘", "politics.legislature", 3.0, "2026-11-01T02:00:00Z", 3), event,
				"2026-11-01T02:00:00Z");
		this.jdbc.sql("""
				update events set status = 'CLOSED', closed_at = '2026-11-02T00:00:00Z', representative_article_id = :article
				where id = :event
				""").param("article", representative).param("event", event).update();
		long storyline = this.rows.storyline("2026-11-01T00:00:00Z", "2026-11-01T02:00:00Z", event);
		this.rows.member(storyline, event);
		subscribeStoryline(storyline, "USER");

		assertThat(events(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(legislature + ":INTEREST");
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
	void signalsAfterThePeriodEndAreReadUpToNow() {
		// 스토리라인 구독(USER)이 기간 끝 11-06 0시 뒤 12시다 — 지금이 그 뒤면 읽혀 무게 1 로 세고(≈ 1 + 0.5 × 1 ÷ 3 ≈ 1.17,
		// 3.6 × 1.17 ≈ 4.2 가 4.0 앞), 지금이 그 앞이면 읽는 범위 밖이라 중립 순서다
		long legislature = event(eventArticle("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1));
		subscribeStoryline(legislatureStoryline(), "USER", "2026-11-06T12:00:00Z");

		assertThat(events(selectionAt("2026-11-07T00:00:00Z").select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(legislature + ":INTEREST");
		assertThat(events(selectionAt("2026-11-06T06:00:00Z").select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(this.e1 + ":SUBSCRIBED");
	}

	/** 시계를 고정한 후보 선별 */
	private CandidateSelection selectionAt(String now) {
		return new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, this.lineage), this.tagSets, this.articles, this.events,
				this.storylines, this.interestMaterials, this.feedback, InstantSource.fixed(Instant.parse(now)));
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
				this.storylineSubscriptions, this.storylines, this.events);
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
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z");
		long[] shown = new long[count];
		for (int i = 0; i < count; i++) {
			long article = article("의회 과거 " + i, "politics.legislature", 3.0, "2026-11-01T00:00:00Z", 1);
			shown[i] = this.rows.event(1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
			this.rows.assign(article, shown[i], "2026-11-01T00:00:00Z");
			DigestRows.item(this.jdbc, digest, shown[i]);
			react(shown[i], "LIKE", "2026-11-04T07:00:00Z");
		}
		return shown;
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

	private void subscribeStoryline(long storyline, String cause) {
		subscribeStoryline(storyline, cause, "2026-11-05T12:00:00Z");
	}

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
	 * 소스)라 보도 강도 = 15 ÷ (1 × 1일) = 15 — 큰 판정 기준의 처음 값 15.00 과 같아 크다(경계 포함)
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
