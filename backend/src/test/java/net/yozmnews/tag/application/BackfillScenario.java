package net.yozmnews.tag.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TagReread;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.domain.result.EvaluationRecord;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import net.yozmnews.tag.domain.threshold.ThresholdValues;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시험 픽스처 — 빠진 태그 채움 시험이 함께 쓰는 체계 · 기준 · 평가 행 만들기와 뒤처리. 기준 변경 · 평가 시각은 고정 시각으로 넣는다.
 * <p>
 * 평가 행은 SQL 로 넣은 판정 원본을 가리키고, 원본마다 다시 읽기의 결과를 대본으로 남긴다 — 회차는 {@link #reread} 를 대본으로 쓰는
 * {@link ScriptedTagging} 으로 돈다. 원본 응답은 해석하지 않는다(관문의 일).
 */
final class BackfillScenario {

	/** 고르지 않은 주제 · 지역의 다시 읽은 확률 — 어느 저장 기준(0.05 이상)에도 저장되지 않는다 */
	static final double OTHERS = 0.01;

	private final TagSetAdministration administration;

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final EvaluationResultRepository results;

	private final TransactionTemplate transaction;

	private final JdbcClient jdbc;

	/** 판정 원본마다 다시 읽기의 결과 — 질문 묶음을 받아 낸다 */
	private final Map<TagJudgementId, Function<TagQuestions, TagReread>> rereads = new HashMap<>();

	BackfillScenario(TagSetAdministration administration, TagSetRepository tagSets, ThresholdRepository thresholds,
			EvaluationResultRepository results, TransactionTemplate transaction, JdbcClient jdbc) {
		this.administration = administration;
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.results = results;
		this.transaction = transaction;
		this.jdbc = jdbc;
	}

	/** 평가 · 태그 · 판정 원본 · 채움 행과 버전 2 이상을 지우고 활성 버전의 기준을 처음 값으로 */
	void reset() {
		this.rereads.clear();
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		this.jdbc.sql("delete from article_tag_judgements").update();
		TagBackfillRows.removeAll(this.jdbc);
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

	/** 지금 활성 버전의 원문으로 다음 버전을 그 시각에 만들어 활성으로 — 처음 값(주제 · 지역 저장 기준 0.1)의 시각이 그 시각이다 */
	TagSetId nextVersionAt(Instant at) {
		TagSetId active = this.tagSets.findByActiveTrue().orElseThrow().id();
		return TagSetVersions.activate(this.administration.versionText(active).orElseThrow(), this.tagSets,
				this.thresholds, this.transaction, at);
	}

	/** 그 버전 주제의 저장 기준을 그 시각에 바꾼다 — 태그 기준은 그대로 */
	void changeTopicStoreAt(TagSetId version, String store, Instant at) {
		this.transaction.executeWithoutResult(status -> {
			ThresholdValues current = this.thresholds.lockCurrent(version, MultiTagKind.TOPIC).orElseThrow();
			this.thresholds.apply(version, MultiTagKind.TOPIC, current,
					new ThresholdValues(current.tagThreshold(), new BigDecimal(store)), ChangeActor.OPERATOR, at);
		});
	}

	/**
	 * 다시 읽히는 평가 하나를 넣는다 — 다시 읽으면 고른 주제(전체 키) · 지역의 확률, 나머지는 {@value #OTHERS} 다. 태그 행은 넘긴
	 * 것만(평가 때 저장 기준으로 거른 모양)
	 */
	TagJudgementId insertRead(TagSetId version, long article, Instant at, Map<String, Double> topics,
			Map<String, Double> regions, List<StoredTag> stored) {
		TagJudgementId judgement = insert(version, article, at, "jev", stored);
		this.rereads.put(judgement,
				questions -> new TagReread.Read(ScriptedTagging.answers(questions, OTHERS, topics, regions)));
		return judgement;
	}

	/** 다시 읽지 못하는 평가 하나를 넣는다 — 다른 평가기의 원본은 평가기 이름을 {@code fake} 로 남긴다 */
	void insertUnread(TagSetId version, long article, Instant at, TagReread.Unread.Reason reason,
			List<StoredTag> stored) {
		String evaluator = (reason == TagReread.Unread.Reason.OTHER_EVALUATOR) ? "fake" : "jev";
		TagJudgementId judgement = insert(version, article, at, evaluator, stored);
		this.rereads.put(judgement, questions -> new TagReread.Unread(reason, "시험이 정한 못 읽음 — " + reason));
	}

	/** 평가 하나를 넣는다 — 다시 읽기 대본이 없다(회차가 다시 읽으면 크게 실패한다) */
	TagJudgementId insert(TagSetId version, long article, Instant at, String evaluator, List<StoredTag> stored) {
		TagJudgementId judgement = TagJudgementRows.insert(this.jdbc, article, evaluator, "model-1", "{}", 3000, at);
		this.results.insert(new EvaluationRecord(new ArticleId(article), version, judgement, "straight", 0.5,
				Map.of("straight", 0.5), 3.0, Map.of(1, 1.0), stored, at));
		return judgement;
	}

	/** 넣은 판정 원본의 다시 읽기 대본 — 대본이 없는 원본이면 크게 실패한다 */
	TagReread reread(TagJudgementId judgement, TagQuestions questions) {
		Function<TagQuestions, TagReread> script = this.rereads.get(judgement);
		if (script == null) {
			throw new AssertionError("다시 읽기 대본이 없는 판정 원본이다 — 원본 " + judgement.value());
		}
		return script.apply(questions);
	}

	/** 그 기사의 태그 행 — 태그 종류 · 키 순 */
	List<StoredTag> tags(long article) {
		return this.jdbc.sql("""
				select tag_kind, tag_key, probability from article_tags where article_id = :article
				order by tag_kind, tag_key
				""")
			.param("article", article)
			.query((rs, row) -> new StoredTag(MultiTagKind.valueOf(rs.getString("tag_kind")), rs.getString("tag_key"),
					rs.getDouble("probability")))
			.list();
	}

	static StoredTag topic(String key, double probability) {
		return new StoredTag(MultiTagKind.TOPIC, key, probability);
	}

	static StoredTag region(String key, double probability) {
		return new StoredTag(MultiTagKind.REGION, key, probability);
	}

}
