package net.yozmnews.tag.domain.result;

import static net.yozmnews.tag.domain.result.ResultTagSet.ASSEMBLY;
import static net.yozmnews.tag.domain.result.ResultTagSet.EXECUTIVE;
import static net.yozmnews.tag.domain.result.ResultTagSet.FINANCE;
import static net.yozmnews.tag.domain.result.ResultTagSet.TAG_SET;
import static net.yozmnews.tag.domain.result.ResultTagSet.region;
import static net.yozmnews.tag.domain.result.ResultTagSet.thresholds;
import static net.yozmnews.tag.domain.result.ResultTagSet.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagAnswers;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetThresholds;
import org.junit.jupiter.api.Test;

/**
 * 결과 해석 — 저장 기준 거르기 · 형식 대표값 · 중요도 기대값 · 근거 값 옮기기.
 * <p>
 * 지키는 규칙: tag-R15
 */
class EvaluationRecordTests {

	private static final ArticleId ARTICLE = new ArticleId(42);

	private static final Instant AT = Instant.parse("2026-11-02T00:00:00Z");

	private static final TagJudgementId JUDGEMENT = new TagJudgementId(7);

	/** 저장 기준 0.1 (주제 · 지역) */
	private static final TagSetThresholds STORE_0_1 = thresholds("0.5", "0.1", "0.5", "0.1");

	@Test
	void storesTopicsAndRegionsAtOrAboveTheStoreThresholdInTagSetOrder() {
		TagAnswers evaluation = evaluation(Map.of(EXECUTIVE, 0.09, ASSEMBLY, 0.1, FINANCE, 0.7),
				Map.of("world", 0.05, "domestic", 0.9), Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25));

		EvaluationRecord record = EvaluationRecord.of(ARTICLE, JUDGEMENT, evaluation, TAG_SET, STORE_0_1, AT);

		assertThat(record.tags()).containsExactly(topic(ASSEMBLY, 0.1), topic(FINANCE, 0.7), region("domestic", 0.9));
	}

	@Test
	void eachTagKindIsStoredByItsOwnStoreThreshold() {
		// 주제 저장 기준 0.1 · 지역 저장 기준 0.3 — 같은 확률 0.25 가 주제로는 남고 지역으로는 버려진다
		TagAnswers evaluation = evaluation(Map.of(EXECUTIVE, 0.25, ASSEMBLY, 0.0, FINANCE, 0.0),
				Map.of("world", 0.25, "domestic", 0.375), Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25));

		EvaluationRecord record = EvaluationRecord.of(ARTICLE, JUDGEMENT, evaluation, TAG_SET,
				thresholds("0.5", "0.1", "0.5", "0.3"), AT);

		assertThat(record.tags()).containsExactly(topic(EXECUTIVE, 0.25), region("domestic", 0.375));
	}

	@Test
	void storedTagsFilterByTheGivenStoreThresholdIncludingTheBoundary() {
		// 채움이 넘기는 저장 기준(0.05)으로 거른다 — 0.05 는 0.050 이상이고 태그 기준은 쓰지 않는다
		TagAnswers evaluation = evaluation(Map.of(EXECUTIVE, 0.04, ASSEMBLY, 0.05, FINANCE, 0.7),
				Map.of("world", 0.049, "domestic", 0.05), Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25));

		assertThat(EvaluationRecord.storedTags(evaluation, TAG_SET, thresholds("0.9", "0.05", "0.9", "0.050")))
			.containsExactly(topic(ASSEMBLY, 0.05), topic(FINANCE, 0.7), region("domestic", 0.05));
	}

	@Test
	void storedTagsOfAnotherVersionAreACallerDefect() {
		TagAnswers evaluation = evaluation(Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5),
				Map.of("world", 0.5, "domestic", 0.5), Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25));

		assertThatIllegalArgumentException().isThrownBy(() -> EvaluationRecord.storedTags(evaluation, TAG_SET,
				new TagSetThresholds(new TagSetId(2), STORE_0_1.topic(), STORE_0_1.region())));
	}

	@Test
	void theRepresentativeFormatIsTheMostLikelyAndATieGoesToTheEarlierOption() {
		TagAnswers tie = evaluation(Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5), Map.of("world", 0.5,
				"domestic", 0.5), Map.of("feature", 0.2, "opinion", 0.4, "straight", 0.4));
		TagAnswers clear = evaluation(Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5), Map.of("world", 0.5,
				"domestic", 0.5), Map.of("straight", 0.25, "opinion", 0.25, "feature", 0.5));

		EvaluationRecord tied = EvaluationRecord.of(ARTICLE, JUDGEMENT, tie, TAG_SET, STORE_0_1, AT);
		EvaluationRecord chosen = EvaluationRecord.of(ARTICLE, JUDGEMENT, clear, TAG_SET, STORE_0_1, AT);

		assertThat(tied.formatKey()).isEqualTo("straight");
		assertThat(tied.formatProbability()).isEqualTo(0.4);
		assertThat(chosen.formatKey()).isEqualTo("feature");
		assertThat(chosen.formatProbability()).isEqualTo(0.5);
		assertThat(chosen.formatDistribution()).isEqualTo(Map.of("straight", 0.25, "opinion", 0.25, "feature", 0.5));
	}

	@Test
	void carriesTheJudgementAndTheImportanceExpectation() {
		TagAnswers evaluation = new TagAnswers(Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5),
				Map.of("world", 0.5, "domestic", 0.5), Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25),
				Map.of(1, 0.5, 2, 0.0, 3, 0.5, 4, 0.0, 5, 0.0));

		EvaluationRecord record = EvaluationRecord.of(ARTICLE, JUDGEMENT, evaluation, TAG_SET, STORE_0_1, AT);

		assertThat(record.articleId()).isEqualTo(ARTICLE);
		assertThat(record.tagSetId()).isEqualTo(new TagSetId(1));
		assertThat(record.judgementId()).isEqualTo(new TagJudgementId(7));
		assertThat(record.evaluatedAt()).isEqualTo(AT);
		assertThat(record.importance()).isEqualTo(2.0);
		assertThat(record.importanceDistribution()).isEqualTo(Map.of(1, 0.5, 2, 0.0, 3, 0.5, 4, 0.0, 5, 0.0));
	}

	@Test
	void rejectsThresholdsOfAnotherVersion() {
		TagSetThresholds other = new TagSetThresholds(new TagSetId(2), STORE_0_1.topic(), STORE_0_1.region());
		TagAnswers evaluation = evaluation(Map.of(EXECUTIVE, 0.5, ASSEMBLY, 0.5, FINANCE, 0.5),
				Map.of("world", 0.5, "domestic", 0.5), Map.of("straight", 0.5, "opinion", 0.25, "feature", 0.25));

		assertThatIllegalArgumentException()
			.isThrownBy(() -> EvaluationRecord.of(ARTICLE, JUDGEMENT, evaluation, TAG_SET, other, AT));
	}

	private static TagAnswers evaluation(Map<String, Double> topics, Map<String, Double> regions,
			Map<String, Double> formats) {
		return new TagAnswers(topics, regions, formats, Map.of(1, 0.2, 2, 0.2, 3, 0.2, 4, 0.2, 5, 0.2));
	}

}
