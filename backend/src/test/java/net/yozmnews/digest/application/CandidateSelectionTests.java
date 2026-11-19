package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import net.yozmnews.digest.domain.input.StorylineGroup;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.ArticleFeedback;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.ReaderRows;
import net.yozmnews.storyline.StorylineDirectory;
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
 * 후보 선별의 재료 경로 — 실제 PostgreSQL 의 사용자 설정 · 기사 · 평가 · 사건 행에서 관심 설정 조회 · 첫 평가 시각 · 평가 · 발행 시각 · 소스 ·
 * 사건 · 대표 기사 · 내용 조회를 거쳐 규칙까지. 기본 체계(버전 1)로 본다. 기간은 2026-11-05 하루(UTC)이고 기사는 모두 그날 0시 발행이다
 * ({@link EventArticles#insert}).
 * <p>
 * 지키는 규칙: 구독 필터 · 사건 단위 중복 제거 · 상한 · 선정 이유, 관심 설정과 계보 표의 활성 버전 대조, 관심도 순위와 {@code INTEREST}
 * (노출 · 좋아요 · 주제 구독 변경 · 스토리라인 구독 변경), 싫어요 거름(구독 후보 · 큰 슬롯 대표), 중립 결과의 토큰 어림, 관심도 재료를
 * 읽는 범위의 끝(부르는 시각), 생성의 입력(프로필 · 후보 재료 · 큰 스토리라인 묶음과 그 몫)
 * <p>
 * 관심도의 기준 시각은 기간 끝 11-06 0시다. 노출 · 반응 · 구독 변경은 그 앞 하루 남짓이라 무게가 1 에 가깝고, 기대값은 순위가 바뀌는지만 본다
 * — 무게의 값은 산식 단위 시험(InterestFormulaTests)이 고정한다.
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

	/** 후보 하나 = 제목 5 + 요약문 "요약" 2 + 80 = 87 토큰. 예산 = 2,000 + 구독 행 1 × 20 + 87 → 하나만 */
	private static final LlmModel ONE_CANDIDATE = new LlmModel(Provider.OPENAI, "gpt-test", 2_000 + 20 + 200 + 87,
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
	ArticleFeedback feedback;

	@Autowired
	ExposureRepository exposures;

	@Autowired
	TopicSubscriptionHistory topicHistory;

	@Autowired
	StorylineSubscriptionDirectory storylineSubscriptions;

	private ReaderRows rows;

	private long user;

	private long a1;

	private long a2;

	private long a5;

	private long a6;

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@BeforeEach
	void prepare() {
		this.rows = new ReaderRows(this.jdbc);
		clean();
		this.user = user();
		// 1 · 2: 같은 사건, 정치 행정 — 대표는 같은 발행 시각이라 ID 가 작은 1(event-R14)
		this.a1 = article("정치 하나", "politics.executive", 4.0, "2026-11-05T06:00:00Z", 1);
		this.a2 = article("정치 둘", "politics.executive", 3.0, "2026-11-05T06:00:00Z", 1);
		long event = this.rows.event(2, "2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z");
		this.rows.assign(this.a1, event, "2026-11-05T00:00:00Z");
		this.rows.assign(this.a2, event, "2026-11-05T00:00:00Z");
		// 3: 제외 소스 2 만 — 빠짐. 4: 구독 밖 주제 — 빠짐. 5: 사건 미배정 — 남음. 6: 첫 평가가 기간 전 — 빠짐
		article("정치 셋", "politics.legislature", 2.0, "2026-11-05T06:00:00Z", 2);
		article("시장", "economy.markets", 5.0, "2026-11-05T06:00:00Z", 1);
		this.a5 = article("정치 다섯", "politics.executive", 1.0, "2026-11-05T06:00:00Z", 1);
		this.a6 = article("정치 여섯", "politics.executive", 5.0, "2026-11-04T23:59:59Z", 1);
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
	}

	@Test
	void theSubscribedArticlesOfThePeriodComeOncePerEventInRankOrder() {
		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).containsExactly(
				new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));
	}

	@Test
	void theInputCarriesTheProfileAndTheCandidateMaterialWithActiveNames() {
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
		CandidateArticle first = input.candidates().getFirst();
		assertThat(first.candidate()).isEqualTo(new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED));
		assertThat(first.title()).isEqualTo("정치 하나");
		assertThat(first.summary()).isEqualTo("요약");
		assertThat(first.topics()).containsExactly("대통령실·행정부");
		assertThat(first.regions()).containsExactly("북한·한반도", "중국");
		assertThat(input.storylines()).isEmpty();
	}

	@Test
	void aPeriodWithoutArticlesStillGivesTheProfile() {
		DigestPeriod empty = new DigestPeriod(Instant.parse("2026-12-05T00:00:00Z"),
				Instant.parse("2026-12-06T00:00:00Z"));

		DigestInput input = this.selection.select(new UserId(this.user), empty, ROOMY).orElseThrow();

		assertThat(input.candidates()).isEmpty();
		assertThat(input.storylines()).isEmpty();
		assertThat(input.profile().topics()).containsExactly("정치");
	}

	@Test
	void theInputTokenBudgetCutsTheTail() {
		// 후보 하나 = 제목 "정치 하나" 5 + 요약문 "요약" 2 + 80 = 87 토큰.
		// 예산 = 지시 2,000 + 프로필(구독 행 1 × 20 + 고정 틀 200) + 87 → 하나만
		LlmModel tight = new LlmModel(Provider.OPENAI, "gpt-test", 2_000 + 20 + 200 + 87, true);

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, tight)))
			.containsExactly(new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED));
	}

	@Test
	void aPeriodLongerThanTheReadLimitIsReadBackFromItsEnd() {
		// 9월 1일 ~ 11월 6일(66일)은 평가 조회의 상한(32일)을 넘는다 — 끝에서 32일 앞인 10월 5일 0시부터 읽는다.
		// 6(첫 평가 11월 4일)은 들고 7(첫 평가 10월 1일)은 빠진다. 8 은 첫 평가가 11월 1일이지만 발행이 10월 1일이라 줄인 시작의
		// 24시간 전(10월 4일 0시)보다 앞서 빠진다 — 발행 시각의 하한도 줄인 시작에서 잰다
		article("정치 일곱", "politics.executive", 5.0, "2026-10-01T00:00:00Z", 1);
		long a8 = article("정치 여덟", "politics.executive", 5.0, "2026-11-01T00:00:00Z", 1);
		this.jdbc.sql("update articles set published_at = '2026-10-01T00:00:00Z' where id = :id").param("id", a8).update();
		DigestPeriod long66Days = new DigestPeriod(Instant.parse("2026-09-01T00:00:00Z"),
				Instant.parse("2026-11-06T00:00:00Z"));

		assertThat(candidates(this.selection.select(new UserId(this.user), long66Days, ROOMY))).containsExactly(
				new DigestCandidate(new ArticleId(this.a6), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));
	}

	@Test
	void aBigStorylineOutsideTheSubscriptionsComesFirstAsTheBigSlotAndIsGrouped() {
		long[] conflict = bigStoryline();

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		// 큰 슬롯: 사건의 대표는 같은 발행 시각이라 ID 가 가장 작은 기사(event-R14), 그 다음 구독 후보
		assertThat(input.digestCandidates()).containsExactly(
				new DigestCandidate(new ArticleId(conflict[0]), SelectionReason.MAJOR_STORYLINE),
				new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));
		// 묶음 이름 = 스토리라인 대표 사건의 대표 기사 제목
		assertThat(input.storylines())
			.containsExactly(new StorylineGroup("분쟁 하나", List.of(new ArticleId(conflict[0]))));
		assertThat(input.candidates().getFirst().topics()).containsExactly("분쟁·안보");
	}

	@Test
	void theStorylineGroupNameMayBeAnArticleOutsideTheCandidates() {
		// 평가 없는 기사가 사건에서 ID 가 가장 작아 사건 전체의 대표다 — 묶음 이름은 그 기사의 제목이고, 그 기사는 후보가 아니다
		long unevaluated = EventArticles.insert(this.jdbc, "분쟁 영", "요약", 1, 11);
		long[] conflict = bigStoryline(unevaluated);

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(input.storylines())
			.containsExactly(new StorylineGroup("분쟁 영", List.of(new ArticleId(conflict[0]))));
		assertThat(input.digestCandidates()).extracting(DigestCandidate::articleId)
			.doesNotContain(new ArticleId(unevaluated));
	}

	@Test
	void aStorylineGroupWhoseRepresentativeCannotBeReadIsNamedAfterItsFirstArticle() {
		// 대표 기사는 "분쟁 영"이지만 그 사이 지워져 읽지 못한 것처럼 대표 조회가 비면, 묶음 첫 기사(큰 슬롯 후보 "분쟁 하나")의 제목이다
		long unevaluated = EventArticles.insert(this.jdbc, "분쟁 영", "요약", 1, 11);
		long[] conflict = bigStoryline(unevaluated);
		EventDirectory noRepresentatives = (EventDirectory) Proxy.newProxyInstance(
				EventDirectory.class.getClassLoader(), new Class<?>[] { EventDirectory.class },
				(proxy, method, args) -> method.getName().equals("representativesOfAll") ? Map.of()
						: method.invoke(this.events, args));
		CandidateSelection selection = new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, this.lineage), this.tagSets, this.articles, noRepresentatives,
				this.storylines, this.interestMaterials, this.feedback, InstantSource.system());

		DigestInput input = selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(input.storylines())
			.containsExactly(new StorylineGroup("분쟁 하나", List.of(new ArticleId(conflict[0]))));
	}

	@Test
	void theStorylineGroupTakesItsShareOfTheBudget() {
		bigStoryline();
		// 예산 = 지시 2,000 + 프로필 220 + 묶음("분쟁 하나" 5 + 100) + 큰 슬롯 기사 87 → 구독 후보는 들 자리가 없다
		LlmModel tight = new LlmModel(Provider.OPENAI, "gpt-test", 2_000 + 220 + 105 + 87, true);

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, tight)))
			.extracting(DigestCandidate::reason)
			.containsExactly(SelectionReason.MAJOR_STORYLINE);

		// 대조: 큰 슬롯을 끄면 묶음 몫이 없어 같은 예산(후보 몫 192)에 구독 후보 둘(87 + 87)이 든다
		this.jdbc.sql("update user_settings set big_slots = 0 where user_id = :user").param("user", this.user).update();
		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, tight))).containsExactly(
				new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));
	}

	@Test
	void zeroBigSlotsLeaveTheBigStorylineOut() {
		bigStoryline();
		this.jdbc.sql("update user_settings set big_slots = 0 where user_id = :user").param("user", this.user).update();

		DigestInput input = this.selection.select(new UserId(this.user), DAY, ROOMY).orElseThrow();

		assertThat(input.digestCandidates()).containsExactly(
				new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));
		assertThat(input.storylines()).isEmpty();
	}

	@Test
	void anArticleTheUserExcludesIsSkippedForTheBigSlotRepresentative() {
		long[] conflict = bigStoryline();
		// 사용자가 지역 us 를 제외하고 가장 작은 ID 의 기사에 지역 us 가 붙는다 — 대표는 그다음 기사. 소스는 그대로라 보도 강도도 그대로다
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'REGION', 'us', 'EXCLUDE', now())
				""").param("user", this.user).update();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				select id, article_id, 'REGION', 'us', 0.9 from article_evaluations where article_id = :article
				""").param("article", conflict[0]).update();

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).first()
			.isEqualTo(new DigestCandidate(new ArticleId(conflict[1]), SelectionReason.MAJOR_STORYLINE));
	}

	@Test
	void anArticleWithoutAnEvaluationCannotBeTheBigSlotRepresentative() {
		// 평가 없는 기사가 사건에서 ID 가 가장 작다 — 지역 · 형식 제외를 확인할 수 없어 대표 자격이 없고 평가 있는 다음 기사가 대표다(digest-R19).
		// 중요도가 없어 중요도 합 · 소스는 그대로라 보도 강도도 그대로다
		long unevaluated = EventArticles.insert(this.jdbc, "분쟁 영", "요약", 1, 11);
		long[] conflict = bigStoryline(unevaluated);

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).first()
			.isEqualTo(new DigestCandidate(new ArticleId(conflict[0]), SelectionReason.MAJOR_STORYLINE));
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

		assertThat(candidates(once.select(new UserId(this.user), DAY, ROOMY))).extracting(DigestCandidate::articleId)
			.containsExactly(new ArticleId(this.a1), new ArticleId(this.a5));
		assertThat(skewed).hasValue(0);

		AtomicInteger always = new AtomicInteger(Integer.MAX_VALUE);
		CandidateSelection never = new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, skewing(always, elsewhere)), this.tagSets, this.articles, this.events,
				this.storylines, this.interestMaterials, this.feedback, InstantSource.system());
		assertThatIllegalStateException().isThrownBy(() -> never.select(new UserId(this.user), DAY, ROOMY))
			.withMessageContaining("2 번 어긋났다");
	}

	@Test
	void anArticleOnATopicTheUserLikedEntersByInterest() {
		// 입법 3.6 은 중립이면 행정 4.0(1) 뒤라 후보 하나의 예산 밖이다. 지난 다이제스트가 입법 기사 일곱을 보였고 모두 좋아요 —
		// 입법 가중치 ≈ 1 + 0.5 × 7 ÷ 10 ≈ 1.35 라 3.6 × 1.35 ≈ 4.8 이 4.0 앞이다
		long legislature = article("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
		long[] shown = likedLegislatureShown(7);

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(legislature), SelectionReason.INTEREST));

		// 대조 — 좋아요를 모두 없음으로 되돌리면 중립 순서 그대로
		for (long article : shown) {
			react(article, "NONE", "2026-11-04T08:00:00Z");
		}
		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED));
	}

	@Test
	void aSubscribedStorylineRaisesTheTopicsOfItsEvents() {
		// 입법 기사 하나뿐인 사건의 스토리라인을 구독했다 — 입법 몫 1, 가중치 ≈ 1 + 0.5 × 1 ÷ 3 ≈ 1.17 이라 3.6 × 1.17 ≈ 4.2 가 4.0 앞이다
		long legislature = article("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
		subscribeStoryline(legislatureStoryline(), "USER");

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(legislature), SelectionReason.INTEREST));
	}

	@Test
	void aSubscribedStorylineCountsTheTopicOfEachEventByItsMembersNotByItsRepresentative() {
		// 지난 사건의 대표 기사는 경제, 나머지 소속 둘은 입법 — 사건의 주제는 입법(2/3)이라 입법 몫 1, 가중치 ≈ 1.17 이고
		// 3.6 × 1.17 ≈ 4.2 가 4.0 앞이다. 대표 기사로 세면 경제 몫 1 이라 입법 기사는 4.0 뒤다
		long legislature = article("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
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

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(legislature), SelectionReason.INTEREST));
	}

	@Test
	void aStorylineSubscriptionRewrittenByAMergeIsNotASignal() {
		article("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
		subscribeStoryline(legislatureStoryline(), "STORYLINE_MERGED");

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED));
	}

	@Test
	void aDislikedArticleLeavesItsEventToAnotherRepresentative() {
		// 1 · 2 는 같은 사건 — 1 에 싫어요를 누르면 대표는 2
		dislike(this.a1);

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).containsExactly(
				new DigestCandidate(new ArticleId(this.a2), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));

		// 대조 — 좋아요로 바꾸면 다시 대표다
		like(this.a1);
		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).containsExactly(
				new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED),
				new DigestCandidate(new ArticleId(this.a5), SelectionReason.SUBSCRIBED));
	}

	@Test
	void aDislikedArticleIsSkippedForTheBigSlotRepresentative() {
		long[] conflict = bigStoryline();
		dislike(conflict[0]);

		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).first()
			.isEqualTo(new DigestCandidate(new ArticleId(conflict[1]), SelectionReason.MAJOR_STORYLINE));

		// 대조 — 좋아요로 바꾸면 다시 큰 슬롯 대표다
		like(conflict[0]);
		assertThat(candidates(this.selection.select(new UserId(this.user), DAY, ROOMY))).first()
			.isEqualTo(new DigestCandidate(new ArticleId(conflict[0]), SelectionReason.MAJOR_STORYLINE));
	}

	@Test
	void signalsAfterThePeriodEndAreReadUpToNow() {
		// 스토리라인 구독(USER)이 기간 끝 11-06 0시 뒤 12시다 — 지금이 그 뒤면 읽혀 무게 1 로 세고(≈ 1 + 0.5 × 1 ÷ 3 ≈ 1.17,
		// 3.6 × 1.17 ≈ 4.2 가 4.0 앞), 지금이 그 앞이면 읽는 범위 밖이라 중립 순서다
		long legislature = article("의회 넷이", "politics.legislature", 3.6, "2026-11-05T06:00:00Z", 1);
		subscribeStoryline(legislatureStoryline(), "USER", "2026-11-06T12:00:00Z");

		assertThat(candidates(selectionAt("2026-11-07T00:00:00Z").select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(legislature), SelectionReason.INTEREST));
		assertThat(candidates(selectionAt("2026-11-06T06:00:00Z").select(new UserId(this.user), DAY, ONE_CANDIDATE)))
			.containsExactly(new DigestCandidate(new ArticleId(this.a1), SelectionReason.SUBSCRIBED));
	}

	/** 시계를 고정한 후보 선별 */
	private CandidateSelection selectionAt(String now) {
		return new CandidateSelection(this.interests, this.evaluations,
				new ActiveEvaluations(this.evaluations, this.lineage), this.tagSets, this.articles, this.events,
				this.storylines, this.interestMaterials, this.feedback, InstantSource.fixed(Instant.parse(now)));
	}

	@Test
	void theNeutralResultIsCutByTheSameBudgetWithItsOwnArticles() {
		// 최대 후보 10 · 예산 9 개. 북한(D 4.8)은 뺌 넷으로 가중치 0.5, 외교(Z 4.55)는 더함 넷으로 1.5.
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
			n[i] = article("기사 %02d".formatted(i), "politics.executive", importance[i], "2026-11-05T06:00:00Z", 1);
		}
		long d = article("기사 05", "politics.north-korea", 4.8, "2026-11-05T06:00:00Z", 1);
		long z = article("기사 10", "politics.diplomacy-defense", 4.55, "2026-11-05T06:00:00Z", 1);
		for (int i = 0; i < 4; i++) {
			topicChange("politics.north-korea", "REMOVE");
			topicChange("politics.diplomacy-defense", "ADD");
		}
		LlmModel nine = new LlmModel(Provider.OPENAI, "gpt-test", 2_000 + 20 + 200 + 9 * 87, true);

		List<DigestCandidate> candidates = candidates(this.selection.select(new UserId(this.user), DAY, nine));

		assertThat(candidates).extracting(DigestCandidate::articleId)
			.containsExactly(new ArticleId(z), new ArticleId(n[1]), new ArticleId(n[2]), new ArticleId(n[3]),
					new ArticleId(n[4]), new ArticleId(n[6]), new ArticleId(n[7]), new ArticleId(n[8]),
					new ArticleId(n[9]));
		assertThat(candidates).filteredOn(candidate -> candidate.reason() == SelectionReason.INTEREST)
			.extracting(DigestCandidate::articleId)
			.containsExactly(new ArticleId(z));
		assertThat(candidates).extracting(DigestCandidate::articleId).doesNotContain(new ArticleId(d));
	}

	@Test
	void aMismatchedActiveVersionOfTheInterestMaterialsIsRetriedOnceAndThenRefused() {
		likedLegislatureShown(1);
		TagSetId elsewhere = new TagSetId(this.tagSets.activeTagSet().id().value() + 1_000);
		AtomicInteger skewed = new AtomicInteger(1);
		CandidateSelection once = selectionWithInterestLineage(skewing(skewed, elsewhere));

		assertThat(candidates(once.select(new UserId(this.user), DAY, ROOMY))).extracting(DigestCandidate::articleId)
			.containsExactly(new ArticleId(this.a1), new ArticleId(this.a5));
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
	 * 지난 다이제스트(11-04 06시에 끝냄)가 입법 기사 여럿을 보였고 사용자가 모두 좋아요를 눌렀다 — 기사의 첫 평가는 기간 앞이라 후보가 아니다
	 * @return 보인 기사
	 */
	private long[] likedLegislatureShown(int count) {
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-04", "2026-11-04T06:00:00Z");
		long[] shown = new long[count];
		for (int i = 0; i < count; i++) {
			shown[i] = article("의회 과거 " + i, "politics.legislature", 3.0, "2026-11-01T00:00:00Z", 1);
			DigestRows.cite(this.jdbc, digest, shown[i]);
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

	private void react(long article, String value, String at) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, article_id, value, digest_id, occurred_at)
				values (:user, :article, :value, 1, cast(:at as timestamptz))
				""").param("user", this.user).param("article", article).param("value", value).param("at", at).update();
	}

	private void dislike(long article) {
		this.jdbc.sql("""
				insert into feedback (user_id, article_id, value, digest_id, updated_at)
				values (:user, :article, 'DISLIKE', 1, now())
				""").param("user", this.user).param("article", article).update();
	}

	private void like(long article) {
		this.jdbc.sql("update feedback set value = 'LIKE' where user_id = :user and article_id = :article")
			.param("user", this.user)
			.param("article", article)
			.update();
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
		long storyline = this.rows.storyline("2026-11-05T00:00:00Z", "2026-11-05T00:00:00Z", event);
		this.rows.member(storyline, event);
		return new long[] { b1, b2, b3 };
	}

	/** 입력의 후보 — 계정이 있어야 한다 */
	private static List<DigestCandidate> candidates(Optional<DigestInput> input) {
		return input.orElseThrow().digestCandidates();
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
