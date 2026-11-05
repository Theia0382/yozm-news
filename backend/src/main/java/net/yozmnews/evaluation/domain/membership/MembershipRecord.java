package net.yozmnews.evaluation.domain.membership;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.evaluator.Membership;

/**
 * 저장할 사건 소속 판정 원본 하나 — docs/specs/evaluation.md §4 · evaluation-R38.
 *
 * @param articleId 판정한 기사
 * @param candidates 물은 후보 — 요청 순서
 * @param wordingVersion 질문 문구의 판
 * @param rawResponse 평가기의 원본 응답 — JSON 텍스트
 */
public record MembershipRecord(ArticleId articleId, List<JudgedCandidate> candidates, String evaluator,
		String modelVersion, String wordingVersion, int inputTokens, String rawResponse, Instant judgedAt) {

	/**
	 * @throws IllegalArgumentException 후보가 없거나, 이름 · 버전 · 판이 공백뿐이거나, 토큰 수가 음수일 때
	 */
	public MembershipRecord {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(candidates, "candidates");
		candidates = List.copyOf(candidates);
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("물은 후보가 없다");
		}
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(modelVersion, "modelVersion");
		Objects.requireNonNull(wordingVersion, "wordingVersion");
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(judgedAt, "judgedAt");
		if (evaluator.isBlank() || modelVersion.isBlank() || wordingVersion.isBlank()) {
			throw new IllegalArgumentException("평가기 이름 · 모델 버전 · 문구 판이 비었다");
		}
		if (inputTokens < 0) {
			throw new IllegalArgumentException("입력 토큰 수가 음수다: " + inputTokens);
		}
	}

	/**
	 * 판정 결과를 저장할 모양으로 — 후보는 {@code asked} 의 순서, 확률은 판정 결과에서 읽는다.
	 * @param asked 물은 후보 사건 ID → 질문에 쓴 서술 기사(요청 순서)
	 * @throws IllegalArgumentException 물은 후보의 확률이 판정 결과에 없을 때 — 결과와 물은 후보의 대조는 부르는 쪽이 먼저 한다
	 */
	public static MembershipRecord of(ArticleId articleId, Map<Long, List<ArticleId>> asked, Membership membership,
			String wordingVersion, Instant judgedAt) {
		List<JudgedCandidate> candidates = new ArrayList<>();
		asked.forEach((eventId, articleIds) -> {
			Double probability = membership.probabilities().get(eventId);
			if (probability == null) {
				throw new IllegalArgumentException("물은 후보의 확률이 없다: " + eventId);
			}
			candidates.add(new JudgedCandidate(eventId, articleIds, probability));
		});
		return new MembershipRecord(articleId, candidates, membership.evaluator(), membership.modelVersion(),
				wordingVersion, membership.inputTokens(), membership.rawResponse(), judgedAt);
	}

}
