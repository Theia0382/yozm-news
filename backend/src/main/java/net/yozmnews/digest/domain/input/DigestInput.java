package net.yozmnews.digest.domain.input;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;

/**
 * 다이제스트 생성의 입력 — 후보 선별이 한 번 읽어 낸 사용자 프로필 · 후보 · 큰 스토리라인 묶음. 토큰 어림에 쓴 글을 그대로 담아 프롬프트에
 * 싣는 글과 같다(ADR-0210) — docs/specs/digest.md §5
 *
 * @param profile 사용자 프로필
 * @param candidates 후보 — 생성에 넘기는 순서(큰 슬롯 기사 다음 구독 후보)
 * @param storylines 큰 스토리라인 묶음 — 큰 판정 행 순서. 묶음의 기사는 모두 후보에 있다
 */
public record DigestInput(DigestProfile profile, List<CandidateArticle> candidates, List<StorylineGroup> storylines) {

	/**
	 * @throws IllegalArgumentException 같은 기사가 후보에 두 번 있거나, 묶음의 기사가 후보에 없거나 두 묶음에 있을 때
	 */
	public DigestInput {
		Objects.requireNonNull(profile, "profile");
		candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
		storylines = List.copyOf(Objects.requireNonNull(storylines, "storylines"));
		Set<ArticleId> seen = new HashSet<>();
		for (CandidateArticle candidate : candidates) {
			if (!seen.add(candidate.candidate().articleId())) {
				throw new IllegalArgumentException("같은 기사가 후보에 두 번 있다: " + candidate.candidate().articleId().value());
			}
		}
		Set<ArticleId> grouped = new HashSet<>();
		for (StorylineGroup group : storylines) {
			for (ArticleId article : group.articles()) {
				if (!seen.contains(article) || !grouped.add(article)) {
					throw new IllegalArgumentException("묶음의 기사가 후보에 없거나 두 묶음에 있다: " + article.value());
				}
			}
		}
	}

	/** 생성에 넘기는 후보 — 기사와 선정 이유, 순서 그대로 */
	public List<DigestCandidate> digestCandidates() {
		return this.candidates.stream().map(CandidateArticle::candidate).toList();
	}

}
