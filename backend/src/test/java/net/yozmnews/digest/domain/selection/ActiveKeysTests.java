package net.yozmnews.digest.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.interest.ExposedArticle;
import net.yozmnews.digest.domain.interest.InterestFormula;
import net.yozmnews.digest.domain.interest.TopicInterests;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import org.junit.jupiter.api.Test;

/**
 * 평가 키를 활성 키로 푼다 — 옛 버전(1)의 평가를 활성 버전(2)의 계보 표로. 버전 2 는 주제 econ.old → econ.new(이름 변경), econ.gone 계보 없이
 * 지움, 지역 · 형식은 그대로다.
 */
class ActiveKeysTests {

	private static final TagSetId V1 = new TagSetId(1);

	private static final TagSetId V2 = new TagSetId(2);

	private static final TagKeyMapping V1_TO_V2 = new TagKeyMapping(V1, V2,
			Map.of(TagKind.TOPIC, Map.of("econ.old", Set.of("econ.new"), "econ.gone", Set.of(), "politics.x",
					Set.of("politics.x")), TagKind.REGION, Map.of("kr", Set.of("kr")), TagKind.FORMAT,
					Map.of("straight", Set.of("straight"))));

	@Test
	void anOldVersionEvaluationIsResolvedToTheActiveKeys() {
		ActiveEvaluation resolved = ActiveKeys.resolve(view(V1, "econ.old", "econ.gone"), V1_TO_V2);

		assertThat(resolved).isEqualTo(new ActiveEvaluation(new ArticleId(7), Set.of("econ.new"), Set.of("kr"),
				Set.of("straight"), TopicStatus.TAGGED, 4.0));
	}

	@Test
	void theReactionOnAnOldVersionArticleGoesToTheActiveSubtopic() {
		// 노출 1 · 좋아요 → x = 1 ÷ 4 → g = 1.125 가 새 키에 간다
		Instant now = Instant.parse("2026-11-17T00:00:00Z");
		ActiveEvaluation resolved = ActiveKeys.resolve(view(V1, "econ.old"), V1_TO_V2);

		TopicInterests interests = InterestFormula
			.interests(List.of(new ExposedArticle(resolved.subtopics(), now, FeedbackValue.LIKE)), List.of(), now);

		assertThat(interests.weight("econ.new")).isCloseTo(1.125, within(1e-12));
		assertThat(interests.weight("econ.old")).isEqualTo(1.0);
	}

	@Test
	void aMappingFromAnotherVersionIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> ActiveKeys.resolve(view(V2, "econ.new"), V1_TO_V2));
	}

	private static ArticleEvaluationView view(TagSetId version, String... topics) {
		return new ArticleEvaluationView(new ArticleId(7), version, TopicStatus.TAGGED,
				List.of(topics).stream().map(key -> new TagProbability(key, 0.9)).toList(), List.of(),
				List.of(new TagProbability("kr", 0.9)), "straight", 4.0);
	}

}
