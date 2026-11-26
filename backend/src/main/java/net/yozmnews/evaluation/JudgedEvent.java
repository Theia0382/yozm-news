package net.yozmnews.evaluation;

import java.util.List;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.membership.RecordedCandidate;

/**
 * 판정 원본에 남은 물은 후보 하나 · docs/specs/evaluation.md §3 · evaluation-R38.
 * <p>
 * 검사는 저장할 때의 물은 후보와 같은 정본({@link RecordedCandidate#checked})이다.
 *
 * @param eventId 판정 당시의 후보 사건 ID — 부르는 쪽({@code event})의 사건
 * @param describedArticles 질문에 쓴 서술 기사 — 요청 순서
 * @param probability 그 사건에 속할 확률
 */
public record JudgedEvent(long eventId, List<ArticleId> describedArticles, double probability) {

	/**
	 * @throws IllegalArgumentException 사건 ID 가 1 미만이거나, 서술 기사가 없거나, 확률이 0 이상 1 이하의 유한값이 아닐 때
	 */
	public JudgedEvent {
		describedArticles = RecordedCandidate.checked(eventId, describedArticles, probability, "사건",
				"JudgedEvent.probability");
	}

}
