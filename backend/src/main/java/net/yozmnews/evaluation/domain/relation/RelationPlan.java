package net.yozmnews.evaluation.domain.relation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.evaluation.StorylineDescriptor;
import net.yozmnews.evaluation.StorylineHappening;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.StorylineText;

/**
 * 물을 서술 고르기 — 판정 요청과 읽은 기사로 포트 입력을 만든다 · docs/specs/evaluation.md §4 · evaluation-R49.
 * <p>
 * 스토리라인마다 서술 사건 가운데 기사가 아직 있는 것의 제목을 요청 순서로 {@link HappeningText#limited} 로 자른다. 지워진 기사와 자른 뒤 공백뿐인
 * 제목의 서술은 뺀다(요청에도 기록에도 없다). 요약문은 표시된 사건의 공백뿐이 아닌 요약문만 싣는다
 * ({@link EvaluationInput#presentSummary}). 한쪽이라도 남은 서술이 없으면 묻지 않는다.
 */
public final class RelationPlan {

	private RelationPlan() {
	}

	/**
	 * @param found 읽은 기사 — 서술 기사 가운데 없는(지운) 기사는 빠져 있다
	 */
	public static Plan of(RelationQuery query, Map<ArticleId, ArticleContent> found, RelationWording wording) {
		Objects.requireNonNull(query, "query");
		Objects.requireNonNull(found, "found");
		Objects.requireNonNull(wording, "wording");
		Side first = side(query.first(), found);
		Side second = side(query.second(), found);
		if (first.texts().isEmpty() || second.texts().isEmpty()) {
			return new Plan.NothingToAsk();
		}
		return new Plan.Ask(new RelationInput(text(query.first(), first), text(query.second(), second), wording),
				first.used(), second.used());
	}

	private static Side side(StorylineDescriptor storyline, Map<ArticleId, ArticleContent> found) {
		List<HappeningText> texts = new ArrayList<>();
		List<RelationRecord.Described> used = new ArrayList<>();
		for (StorylineHappening happening : storyline.described()) {
			ArticleContent content = found.get(happening.articleId());
			if (content == null) {
				continue;
			}
			String summary = happening.summarized() ? EvaluationInput.presentSummary(content.summary()) : null;
			Optional<HappeningText> text = HappeningText.limited(happening.at(), content.title(), summary);
			if (text.isPresent()) {
				texts.add(text.get());
				used.add(new RelationRecord.Described(happening.articleId(), text.get().summary() != null));
			}
		}
		return new Side(texts, used);
	}

	private static StorylineText text(StorylineDescriptor storyline, Side side) {
		return new StorylineText(storyline.happenings(), storyline.firstAt(), storyline.lastAt(), side.texts());
	}

	/** 스토리라인 하나에서 남은 서술 — 요청에 싣는 것과 기록에 남기는 것 */
	private record Side(List<HappeningText> texts, List<RelationRecord.Described> used) {

	}

	/** 물을 것 */
	public sealed interface Plan {

		/**
		 * 묻는다.
		 *
		 * @param first first 에서 요청에 실은 서술 기사(요청 순서)
		 * @param second second 에서 요청에 실은 서술 기사(요청 순서)
		 */
		record Ask(RelationInput input, List<RelationRecord.Described> first, List<RelationRecord.Described> second)
				implements Plan {

			public Ask {
				Objects.requireNonNull(input, "input");
				first = List.copyOf(first);
				second = List.copyOf(second);
			}

		}

		/** 한쪽 스토리라인에 남은 서술이 없어 묻지 않는다 */
		record NothingToAsk() implements Plan {

		}

	}

}
