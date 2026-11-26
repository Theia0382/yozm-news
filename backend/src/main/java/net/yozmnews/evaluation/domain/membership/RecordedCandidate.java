package net.yozmnews.evaluation.domain.membership;

import java.util.List;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.ProbabilityRange;

/**
 * 저장할 판정 원본의 물은 후보 하나 · docs/specs/evaluation.md §4 · evaluation-R38 · evaluation-R52.
 *
 * @param candidateId 물은 후보 ID — 사건 소속이면 사건 ID, 스토리라인 소속이면 부르는 쪽({@code storyline})이 정한 후보 ID(스토리라인이나 이웃
 * 사건)
 * @param describedArticles 질문에 쓴 서술 기사 — 요청 순서
 * @param probability 그 후보에 속할 확률
 */
public record RecordedCandidate(long candidateId, List<ArticleId> describedArticles, double probability) {

	/**
	 * @throws IllegalArgumentException 후보 ID 가 1 미만이거나, 서술 기사가 없거나, 확률이 0 이상 1 이하의 유한값이 아닐 때
	 */
	public RecordedCandidate {
		describedArticles = checked(candidateId, describedArticles, probability, "후보",
				"RecordedCandidate.probability");
	}

	/**
	 * 판정 원본의 물은 후보 하나의 검사 — 정본. 쓸 때의 이 값과 읽을 때의 공개 값 {@link net.yozmnews.evaluation.JudgedEvent} 가 부른다.
	 * @param id 후보 ID
	 * @param describedArticles 질문에 쓴 서술 기사
	 * @param probability 그 후보에 속할 확률
	 * @param noun 후보 ID 의 이름(사건 · 후보) — ID 실패 문구에 싣는다
	 * @param probabilityName 확률 칸의 이름 — 확률 범위 실패 문구에 싣는다
	 * @return 복사한 서술 기사
	 * @throws IllegalArgumentException ID 가 1 미만이거나, 서술 기사가 없거나, 확률이 0 이상 1 이하의 유한값이 아닐 때
	 */
	public static List<ArticleId> checked(long id, List<ArticleId> describedArticles, double probability, String noun,
			String probabilityName) {
		if (id < 1) {
			throw new IllegalArgumentException(noun + " ID 는 1 이상이다: " + id);
		}
		Objects.requireNonNull(describedArticles, "describedArticles");
		List<ArticleId> copied = List.copyOf(describedArticles);
		if (copied.isEmpty()) {
			throw new IllegalArgumentException("서술 기사가 없다");
		}
		ProbabilityRange.require(probability, probabilityName);
		return copied;
	}

}
