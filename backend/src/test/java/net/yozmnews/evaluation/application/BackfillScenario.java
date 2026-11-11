package net.yozmnews.evaluation.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.result.StoredTag;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdValues;
import net.yozmnews.platform.ChangeActor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시험 픽스처 — 빠진 태그 채움 시험이 함께 쓰는 체계 · 기준 · 평가 행 만들기와 뒤처리. 기준 변경 · 평가 시각은 고정 시각으로 넣는다.
 */
final class BackfillScenario {

	private final TagSetAdministration administration;

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	private final EvaluationResultRepository results;

	private final TagSetViews views;

	private final TransactionTemplate transaction;

	private final JdbcClient jdbc;

	BackfillScenario(TagSetAdministration administration, TagSetRepository tagSets, ThresholdRepository thresholds,
			EvaluationResultRepository results, TagSetViews views, TransactionTemplate transaction, JdbcClient jdbc) {
		this.administration = administration;
		this.tagSets = tagSets;
		this.thresholds = thresholds;
		this.results = results;
		this.views = views;
		this.transaction = transaction;
		this.jdbc = jdbc;
	}

	/** 평가 · 태그 · 채움 행과 버전 2 이상을 지우고 활성 버전의 기준을 처음 값으로 */
	void reset() {
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
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

	TagSetView view(TagSetId version) {
		return this.views.of(version);
	}

	/** Jev 평가 하나를 넣는다 — 원본 응답은 고른 확률로 만들고, 태그 행은 넘긴 것만(평가 때 저장 기준으로 거른 모양) */
	void insertJev(TagSetId version, long article, Instant at, Map<String, Double> topics, Map<String, Double> regions,
			List<StoredTag> stored) {
		insert(version, article, at, "jev", JevAnswers.of(view(version), topics, regions), stored);
	}

	/** 평가 하나를 넣는다 — 평가기 이름과 원본 응답을 그대로 */
	void insert(TagSetId version, long article, Instant at, String evaluator, String raw, List<StoredTag> stored) {
		this.results.insert(new EvaluationRecord(new ArticleId(article), version, evaluator, "model-1", "straight", 0.5,
				Map.of("straight", 0.5), 3.0, Map.of(1, 1.0), stored, raw, 3000, at));
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
