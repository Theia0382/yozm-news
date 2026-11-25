package net.yozmnews.evaluation.domain.membership;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;

/**
 * 물을 후보 고르기 — 소속 판정(사건 소속 · 스토리라인 소속) 요청과 읽은 기사로 포트 입력을 만든다 · docs/specs/evaluation.md §4 ·
 * evaluation-R38 · evaluation-R52.
 * <p>
 * 후보마다 서술 기사 가운데 아직 있는 것의 제목을 요청 순서로 {@link CandidateEvent#limited} 로 자른다. 지워진 서술 기사와 자른 뒤
 * 공백뿐인 제목의 서술 기사는 빼고(질문에도 기록에도 없다), 남은 제목이 없는 후보는 묻지 않는다. 판정할 기사의 요약은
 * {@link EvaluationInput#presentSummary} 로 정리한다.
 */
public final class MembershipPlan {

	private MembershipPlan() {
	}

	/**
	 * @param candidates 후보 ID(사건 소속이면 사건 ID) → 서술 기사 — 요청 순서. 요청 값이 이미 검사한 것이다
	 * @param article 판정할 기사의 내용
	 * @param found 읽은 기사 — 서술 기사 가운데 없는(지운) 기사는 빠져 있다
	 */
	public static Plan of(Map<Long, List<ArticleId>> candidates, ArticleContent article,
			Map<ArticleId, ArticleContent> found, MembershipWording wording) {
		Objects.requireNonNull(candidates, "candidates");
		Objects.requireNonNull(article, "article");
		Objects.requireNonNull(found, "found");
		Objects.requireNonNull(wording, "wording");
		List<CandidateEvent> asking = new ArrayList<>();
		Map<Long, List<ArticleId>> asked = new LinkedHashMap<>();
		candidates.forEach((candidateId, articleIds) -> {
			List<ArticleId> used = new ArrayList<>();
			List<String> titles = new ArrayList<>();
			for (ArticleId articleId : articleIds) {
				ArticleContent content = found.get(articleId);
				// 제목 하나씩 자른다 — 자른 뒤 공백뿐이라 버려진 제목의 서술 기사는 기록에서도 뺀다
				Optional<CandidateEvent> one = (content == null) ? Optional.empty()
						: CandidateEvent.limited(candidateId, List.of(content.title()));
				if (one.isPresent()) {
					used.add(articleId);
					titles.addAll(one.get().titles());
				}
			}
			if (!titles.isEmpty()) {
				asking.add(new CandidateEvent(candidateId, titles));
				asked.put(candidateId, List.copyOf(used));
			}
		});
		if (asking.isEmpty()) {
			return new Plan.NothingToAsk();
		}
		return new Plan.Ask(new MembershipInput(article.title(), EvaluationInput.presentSummary(article.summary()),
				asking, wording), asked);
	}

	/** 물을 것 */
	public sealed interface Plan {

		/**
		 * 묻는다.
		 *
		 * @param asked 물은 후보 ID → 질문에 쓴 서술 기사(요청 순서) — 후보 순서는 요청 순서
		 */
		record Ask(MembershipInput input, Map<Long, List<ArticleId>> asked) implements Plan {

			public Ask {
				Objects.requireNonNull(input, "input");
				asked = Collections.unmodifiableMap(new LinkedHashMap<>(asked));
			}

		}

		/** 서술이 남은 후보가 없어 묻지 않는다 */
		record NothingToAsk() implements Plan {

		}

	}

}
