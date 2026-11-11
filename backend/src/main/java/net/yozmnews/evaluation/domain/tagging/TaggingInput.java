package net.yozmnews.evaluation.domain.tagging;

import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import org.jspecify.annotations.Nullable;

/**
 * 태그 판정의 포트 입력 만들기 — 정본 · docs/specs/evaluation.md §4 · evaluation-R40.
 * <p>
 * 피드 맥락은 기사의 엔트리마다 하나이고 엔트리 순서(받은 시각 순, 같으면 피드 ID 순) 그대로다. 이름 · 섹션이 없는 피드(그새 지운 피드
 * 포함)는 비우고, 카테고리는 {@link FeedContext#limited} 로 줄이고, 공백뿐인 요약은 없는 요약이다.
 */
public final class TaggingInput {

	private TaggingInput() {
	}

	/**
	 * @param labels 엔트리 피드의 출처 이름 · 섹션 — 없는 피드는 빠져 있어도 된다
	 */
	public static EvaluationInput of(ArticleContent content, Map<FeedId, FeedLabel> labels) {
		List<FeedContext> feeds = content.entries().stream().map(entry -> {
			@Nullable FeedLabel label = labels.get(entry.feedId());
			return FeedContext.limited((label != null) ? label.sourceName() : null,
					(label != null) ? label.section() : null, entry.categories());
		}).toList();
		return new EvaluationInput(content.title(), EvaluationInput.presentSummary(content.summary()), feeds);
	}

}
