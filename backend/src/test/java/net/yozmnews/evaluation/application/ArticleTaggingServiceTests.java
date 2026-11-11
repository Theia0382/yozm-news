package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagAnswers;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TagReread;
import net.yozmnews.evaluation.TaggingJudgement;
import net.yozmnews.evaluation.TaggingQuery;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRecord;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRepository;
import net.yozmnews.source.FeedLabels;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 태그 판정 공개 API — 기사 읽기와 입력 만들기(엔트리마다의 피드 맥락 · 받은 시각 순 · 공백뿐인 요약), 결과 갈래, 질문 묶음과의 대조, 판정 원본 기록, 판정 중 지운 기사, 다시 읽기 · 원본
 * 조회를 실제 PostgreSQL 과 결선된 저장소 · 기사 조회로 본다. 평가기는 시험이 넣는다. 커밋 경계를 지나므로 시험을 트랜잭션으로 감싸지
 * 않는다.
 * <p>
 * 지키는 규칙: evaluation-R40 · evaluation-R41 · evaluation-R42
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class ArticleTaggingServiceTests {

	private static final Instant AT = Instant.parse("2026-11-05T06:30:00Z");

	private static final String RAW = "{\"model\":\"jev-1.14.0\",\"answers\":{}}";

	/** 시험의 질문 묶음 — 주제 중분류 둘, 지역 둘, 형식 둘, 중요도 수준 둘 */
	private static final TagQuestions QUESTIONS = new TagQuestions(
			List.of(new TopicCategory("politics", "정치", "정치",
					List.of(new AskedTag("politics.executive", "행정부", "행정부", "행정부를 다루는가"),
							new AskedTag("politics.assembly", "국회", "국회", "국회를 다루는가")))),
			List.of(new AskedTag("world", "국제", "국외", "국외의 일인가"),
					new AskedTag("domestic", "국내", "국내", "국내의 일인가")),
			new FormatChoice("형식은", List.of(new FormatOption("straight", "스트레이트", "사실", false),
					new FormatOption("opinion", "오피니언", "의견", true))),
			new ImportanceScale("중요도는", List.of(new ImportanceLevel(1, "작다"), new ImportanceLevel(2, "크다"))));

	/** 질문 묶음과 맞는 답 */
	private static final Evaluation MATCHING = new Evaluation(Map.of("politics.executive", 0.8, "politics.assembly", 0.1),
			Map.of("world", 0.2, "domestic", 0.9), Map.of("straight", 0.7, "opinion", 0.3), Map.of(1, 0.4, 2, 0.6), "jev",
			"jev-1.14.0", 812, RAW);

	@Autowired
	ArticleDirectory articles;

	@Autowired
	FeedLabels feedLabels;

	@Autowired
	TagJudgementRepository judgements;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from article_tag_judgements").update();
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
	}

	@Test
	void judgesTheArticleAndRecordsTheJudgement() {
		long article = article("국회 본회의 통과", "법안이 통과했다", "정치", "국회");
		Stub stub = new Stub(input -> MATCHING);

		TaggingJudgement judgement = service(stub).judge(new TaggingQuery(new ArticleId(article), QUESTIONS));

		assertThat(judgement).isEqualTo(new TaggingJudgement.Judged(new TagJudgementId(judgementId()),
				new TagAnswers(Map.of("politics.executive", 0.8, "politics.assembly", 0.1),
						Map.of("world", 0.2, "domestic", 0.9), Map.of("straight", 0.7, "opinion", 0.3),
						Map.of(1, 0.4, 2, 0.6))));
		// 기사 내용과 엔트리의 피드 맥락 — 지운(없는) 피드라 출처 이름 · 섹션은 비고 카테고리는 그대로
		assertThat(stub.inputs).containsExactly(new EvaluationInput("국회 본회의 통과", "법안이 통과했다",
				List.of(new FeedContext(null, null, List.of("정치", "국회")))));
		assertThat(stub.questions).containsExactly(QUESTIONS);
		Map<String, Object> row = this.jdbc.sql("""
				select article_id, evaluator, model_version, raw_response::text as raw_response, input_tokens, judged_at
				from article_tag_judgements
				""").query().singleRow();
		assertThat(row).containsEntry("article_id", article)
			.containsEntry("evaluator", "jev")
			.containsEntry("model_version", "jev-1.14.0")
			.containsEntry("raw_response", "{\"model\": \"jev-1.14.0\", \"answers\": {}}")
			.containsEntry("input_tokens", 812);
		assertThat(((java.sql.Timestamp) row.get("judged_at")).toInstant()).isEqualTo(AT);
	}

	@Test
	void theInputCarriesEveryEntryInReceivedOrderWithItsFeedLabelAndNoBlankSummary() {
		long source = this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values ('tagging-input', false, 0, now())
				returning id
				""").query(Long.class).single();
		this.jdbc.sql("insert into source_attributions (source_id, name, recorded_at) values (:source, '기록일보', now())")
			.param("source", source)
			.update();
		long feed = this.jdbc.sql("""
				insert into feeds (source_id, feed_key, enabled, version, created_at)
				values (:source, 'all', false, 0, now())
				returning id
				""").param("source", source).query(Long.class).single();
		long article = bareArticle("국회 본회의 통과", "   ");
		// 피드 ID 가 큰(없는) 피드의 엔트리를 먼저 받았다 — 피드 맥락의 순서는 피드 ID 가 아니라 받은 시각이다
		assertThat(feed).as("있는 피드의 ID 가 없는 피드보다 작아야 순서의 오라클이 선다").isLessThan(999_901);
		entry(article, feed, source, "2026-11-05T05:10:00Z", "정치", "국회");
		entry(article, 999_901, 999_900, "2026-11-05T05:00:00Z", "속보");
		Stub stub = new Stub(input -> MATCHING);

		try {
			service(stub).judge(new TaggingQuery(new ArticleId(article), QUESTIONS));
		}
		finally {
			this.jdbc.sql("delete from feeds where id = :feed").param("feed", feed).update();
			this.jdbc.sql("delete from source_attributions where source_id = :source").param("source", source).update();
			this.jdbc.sql("delete from sources where id = :source").param("source", source).update();
		}

		// 없는 피드는 이름 · 섹션이 비고, 사라진 프리셋의 피드는 기록된 출처 이름에 섹션이 없다. 공백뿐인 요약은 없는 요약이다
		assertThat(stub.inputs).containsExactly(new EvaluationInput("국회 본회의 통과", null,
				List.of(new FeedContext(null, null, List.of("속보")),
						new FeedContext("기록일보", null, List.of("정치", "국회")))));
	}

	@Test
	void aMissingArticleIsNotJudged() {
		Stub stub = new Stub(input -> MATCHING);

		TaggingJudgement judgement = service(stub).judge(new TaggingQuery(new ArticleId(987_654), QUESTIONS));

		assertThat(judgement).isEqualTo(new TaggingJudgement.ArticleMissing());
		assertThat(stub.inputs).isEmpty();
		assertThat(judgementRows()).isZero();
	}

	@Test
	void anArticleRemovedDuringTheJudgementLeavesNoJudgement(CapturedOutput output) {
		long article = article("국회 본회의 통과", null);
		Stub stub = new Stub(input -> {
			removeArticle(article);
			return MATCHING;
		});

		TaggingJudgement judgement = service(stub).judge(new TaggingQuery(new ArticleId(article), QUESTIONS));

		assertThat(judgement).isEqualTo(new TaggingJudgement.ArticleMissing());
		assertThat(judgementRows()).isZero();
		assertThat(output).contains("태그 판정 중 기사가 지워져 판정 원본을 지웠다 — 기사 " + article);
	}

	@Test
	void anExpectedFailureIsReturnedWithoutAJudgement() {
		long article = article("국회 본회의 통과", null);

		TaggingJudgement judgement = service(new Stub(input -> new EvaluationFailure(EvaluatorFailureKind.TRANSIENT,
				"Jev 가 503 을 돌려줬다"))).judge(new TaggingQuery(new ArticleId(article), QUESTIONS));

		assertThat(judgement)
			.isEqualTo(new TaggingJudgement.Failed(EvaluatorFailureKind.TRANSIENT, "Jev 가 503 을 돌려줬다"));
		assertThat(judgementRows()).isZero();
	}

	@Test
	void aResultThatDoesNotMatchTheQuestionsIsRefusedWithoutAJudgement() {
		long article = article("국회 본회의 통과", null);
		Evaluation missingKey = new Evaluation(Map.of("politics.executive", 0.8), MATCHING.regions(),
				MATCHING.formats(), MATCHING.importance(), "jev", "jev-1.14.0", 812, RAW);
		Evaluation unstorable = new Evaluation(MATCHING.topics(), MATCHING.regions(), MATCHING.formats(),
				MATCHING.importance(), "jev", "jev-1.14.0", 812, "{\"a\":\"\\u0000\"}");

		assertThatIllegalStateException()
			.isThrownBy(() -> service(new Stub(input -> missingKey)).judge(new TaggingQuery(new ArticleId(article),
					QUESTIONS)))
			.withMessage("태그 판정 결과를 저장할 수 없다 — 주제 키가 체계와 다르다 — 빠진 키 [politics.assembly], 모르는 키 []");
		assertThatIllegalStateException()
			.isThrownBy(() -> service(new Stub(input -> unstorable)).judge(new TaggingQuery(new ArticleId(article),
					QUESTIONS)))
			.withMessage("태그 판정 결과를 저장할 수 없다 — 원본 응답에 문자 U+0000 이 있다");
		assertThat(judgementRows()).isZero();
	}

	@Test
	void judgingInATransactionOrWithoutAnEvaluatorIsACallerDefect() {
		long article = article("국회 본회의 통과", null);
		Stub stub = new Stub(input -> MATCHING);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(status -> service(stub)
				.judge(new TaggingQuery(new ArticleId(article), QUESTIONS))))
			.withMessage(ArticleTaggingService.IN_TRANSACTION);
		assertThatIllegalStateException()
			.isThrownBy(() -> service(null).judge(new TaggingQuery(new ArticleId(article), QUESTIONS)))
			.withMessage(ArticleTaggingService.NO_EVALUATOR);
		// 다시 읽기도 평가기가 없으면 원본을 읽기 전에 멈춘다 — 없는 원본이어도 못 읽음이 아니라 예외다
		assertThatIllegalStateException()
			.isThrownBy(() -> service(null).reread(new TagJudgementId(987_654), QUESTIONS))
			.withMessage(ArticleTaggingService.NO_EVALUATOR);
		assertThat(stub.inputs).isEmpty();
		assertThat(judgementRows()).isZero();
	}

	@Test
	void rereadsTheStoredRawResponseWithTheQuestions() {
		TagJudgementId stored = this.judgements
			.insert(new TagJudgementRecord(new ArticleId(42), "jev", "jev-1.14.0", RAW, 812, AT));
		Stub reading = new Stub(input -> MATCHING, (evaluator, raw) -> MATCHING);
		Stub dropping = new Stub(input -> MATCHING, (evaluator, raw) -> new Evaluation(Map.of("politics.executive", 0.8),
				MATCHING.regions(), MATCHING.formats(), MATCHING.importance(), "jev", "jev-1.14.0", 0, raw));
		Stub other = new Stub(input -> MATCHING,
				(evaluator, raw) -> new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "다른 평가기의 원본이다"));

		assertThat(service(reading).reread(stored, QUESTIONS)).isEqualTo(new TagReread.Read(new TagAnswers(
				MATCHING.topics(), MATCHING.regions(), MATCHING.formats(), MATCHING.importance())));
		// 평가기에는 남긴 평가기 이름과 jsonb 로 저장된 원본(공백이 다르다)이 넘어간다
		assertThat(reading.rereads).containsExactly("jev|{\"model\": \"jev-1.14.0\", \"answers\": {}}");
		assertThat(reading.questions).containsExactly(QUESTIONS);
		assertThat(service(dropping).reread(stored, QUESTIONS)).isEqualTo(new TagReread.Unread(
				TagReread.Unread.Reason.UNREADABLE, "주제 키가 체계와 다르다 — 빠진 키 [politics.assembly], 모르는 키 []"));
		assertThat(service(other).reread(stored, QUESTIONS))
			.isEqualTo(new TagReread.Unread(TagReread.Unread.Reason.OTHER_EVALUATOR, "다른 평가기의 원본이다"));
		TagJudgementId missing = new TagJudgementId(stored.value() + 1);
		assertThat(service(reading).reread(missing, QUESTIONS)).isEqualTo(new TagReread.Unread(
				TagReread.Unread.Reason.UNREADABLE, "태그 판정 원본이 없다 — 원본 " + missing.value()));
	}

	@Test
	void findsTheJudgementViewsWithoutAnEvaluator() {
		TagJudgementId stored = this.judgements
			.insert(new TagJudgementRecord(new ArticleId(42), "fake", "fake-rules-2", "{}", 31, AT));
		TagJudgementId missing = new TagJudgementId(stored.value() + 1);

		assertThat(service(null).find(List.of(stored, missing))).isEqualTo(
				Map.of(stored, new TagJudgementView(stored, new ArticleId(42), "fake", "fake-rules-2", 31, AT)));
	}

	private ArticleTaggingService service(@Nullable ArticleEvaluator evaluator) {
		return new ArticleTaggingService(() -> evaluator, this.articles, this.feedLabels, this.judgements,
				this.transaction, () -> AT);
	}

	/** 기사 하나와 그 엔트리 하나 — 없는 피드 999901(소스 999900), 받은 시각 2026-11-05T05:00:00Z */
	private long article(String title, @Nullable String summary, String... categories) {
		long id = bareArticle(title, summary);
		entry(id, 999_901, 999_900, "2026-11-05T05:00:00Z", categories);
		return id;
	}

	/** 엔트리 없는 기사 하나 */
	private long bareArticle(String title, @Nullable String summary) {
		String url = "https://tagging.example/" + UUID.randomUUID();
		return this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, summary_text, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), :title, sha256(convert_to(:title, 'UTF8')), :summary,
				    '2026-11-05T05:00:00Z', false, '2026-11-05T05:00:00Z')
				returning id
				""").param("url", url).param("title", title).param("summary", summary, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
	}

	/** 기사의 엔트리 하나 — 그 피드 · 소스에서 그 시각에 받았다 */
	private void entry(long article, long feed, long source, String seenAt, String... categories) {
		this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				values (:article, :feed, :source,
				    sha256(convert_to(cast(:article as text) || '/' || cast(:feed as text), 'UTF8')), :categories, false,
				    'NEW', cast(:seen as timestamptz))
				""")
			.param("article", article)
			.param("feed", feed)
			.param("source", source)
			.param("categories", categories)
			.param("seen", seenAt)
			.update();
	}

	private void removeArticle(long article) {
		this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", article).update();
		this.jdbc.sql("delete from articles where id = :id").param("id", article).update();
	}

	private long judgementRows() {
		return this.jdbc.sql("select count(*) from article_tag_judgements").query(Long.class).single();
	}

	/** 하나뿐인 판정 원본의 ID — DB 가 매긴 값 */
	private long judgementId() {
		return this.jdbc.sql("select id from article_tag_judgements").query(Long.class).single();
	}

	/** 평가 · 다시 읽기의 답을 정하고, 받은 입력 · 질문 묶음 · 다시 읽기를 기록한다 */
	private static final class Stub implements ArticleEvaluator {

		private final Function<EvaluationInput, EvaluationResult> answer;

		private final java.util.function.BiFunction<String, String, RereadResult> reread;

		private final List<EvaluationInput> inputs = new ArrayList<>();

		private final List<TagQuestions> questions = new ArrayList<>();

		private final List<String> rereads = new ArrayList<>();

		Stub(Function<EvaluationInput, EvaluationResult> answer) {
			this(answer, (evaluator, raw) -> {
				throw new AssertionError("다시 읽지 않는다");
			});
		}

		Stub(Function<EvaluationInput, EvaluationResult> answer,
				java.util.function.BiFunction<String, String, RereadResult> reread) {
			this.answer = answer;
			this.reread = reread;
		}

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagQuestions asked) {
			this.inputs.add(input);
			this.questions.add(asked);
			return this.answer.apply(input);
		}

		@Override
		public RereadResult reread(String evaluator, String rawResponse, TagQuestions asked) {
			this.rereads.add(evaluator + "|" + rawResponse);
			this.questions.add(asked);
			return this.reread.apply(evaluator, rawResponse);
		}

		@Override
		public MembershipResult judgeMembership(MembershipInput input) {
			throw new AssertionError("사건 소속 판정을 부르지 않는다");
		}

	}

}
