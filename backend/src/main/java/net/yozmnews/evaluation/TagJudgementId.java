package net.yozmnews.evaluation;

/**
 * 태그 판정 원본의 식별자 — {@code article_tag_judgements.id} · docs/specs/evaluation.md §3 · evaluation-R41.
 * <p>
 * 판정을 반영한 쪽({@code tag} 의 평가 행)이 자기 기록에 남겨, 다시 읽기 · 원본 조회에 쓴다.
 */
public record TagJudgementId(long value) {

	/**
	 * @throws IllegalArgumentException 1 보다 작을 때
	 */
	public TagJudgementId {
		if (value < 1) {
			throw new IllegalArgumentException("태그 판정 원본 ID 는 1 이상이다: " + value);
		}
	}

}
