package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.digest.domain.input.StorylineGroup;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 후보 선별의 재료 경로 — 실제 PostgreSQL 의 사용자 설정 · 기사 · 평가 · 사건 행에서 관심 설정 조회 · 첫 평가 시각 · 평가 · 발행 시각 · 소스 ·
 * 사건 · 대표 기사 · 내용 조회를 거쳐 규칙까지. 기본 체계(버전 1)로 본다. 기간은 2026-11-05 하루(UTC)이고 기사는 모두 그날 0시 발행이다
 * ({@link EventArticles#insert}).
 * <p>
 * 지키는 규칙: 구독 필터 · 사건 단위 중복 제거 · 상한 · 선정 이유, 관심 설정과 계보 표의 활성 버전 대조, 생성의 입력(프로필 · 후보 재료 ·
 * 큰 스토리라인 묶음과 그 몫)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class CandidateSelectionTests {

	private static final DigestPeriod DAY = new DigestPeriod(Instant.parse("2026-11-05T00:00:00Z"),
			Instant.parse("2026-11-06T00:00:00Z"));

	private static final LlmModel ROOMY = new LlmModel(Provider.OPENAI, "gpt-test", 32_000, true);

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

	private ReaderRows rows;

	private long user;

	private long a1;

	private long a2;

	private long a5;

	private long a6;

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
		CandidateSelection selection = new CandidateSelection(this.interests, this.evaluations, this.lineage,
				this.tagSets, this.articles, noRepresentatives, this.storylines);

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
				skewing(skewed, elsewhere), this.tagSets, this.articles, this.events, this.storylines);

		assertThat(candidates(once.select(new UserId(this.user), DAY, ROOMY))).extracting(DigestCandidate::articleId)
			.containsExactly(new ArticleId(this.a1), new ArticleId(this.a5));
		assertThat(skewed).hasValue(0);

		AtomicInteger always = new AtomicInteger(Integer.MAX_VALUE);
		CandidateSelection never = new CandidateSelection(this.interests, this.evaluations,
				skewing(always, elsewhere), this.tagSets, this.articles, this.events, this.storylines);
		assertThatIllegalStateException().isThrownBy(() -> never.select(new UserId(this.user), DAY, ROOMY))
			.withMessageContaining("2 번 어긋났다");
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
