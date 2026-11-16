package net.yozmnews.digest.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.removal.DigestContentCleanup;
import net.yozmnews.digest.domain.removal.RemovalFollowUp;
import net.yozmnews.digest.domain.removal.SectionCitations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지운 기사 따르기 — docs/specs/digest.md §4 · digest-R10
 * <p>
 * 한 트랜잭션에서 지운 기사를 후보나 인용으로 가진 다이제스트 행을 ID 순으로 잠근 뒤 후보 행 → 섹션 판단 → 인용 행 → 섹션 순으로
 * 지운다. 같은 다이제스트를 다루는 생성 · 다른 지운 기사 따르기가 있으면 그 커밋을 기다린 뒤, 이어지는 조회가 방금 커밋된 행까지 본다 —
 * 나뉘어 발행된 지운 기사 이벤트가 동시에 처리되어도 서로의 지우기를 놓치지 않는다. 다시 처리해도 결과가 같다.
 */
@Service
public class DigestArticleRemoval {

	private final DigestCandidateRepository candidates;

	private final DigestContentCleanup content;

	DigestArticleRemoval(DigestCandidateRepository candidates, DigestContentCleanup content) {
		this.candidates = candidates;
		this.content = content;
	}

	/** 지운 기사를 다이제스트에서 따른다 */
	@Transactional
	public void apply(Collection<ArticleId> removed) {
		Objects.requireNonNull(removed, "removed");
		if (removed.isEmpty()) {
			return;
		}
		this.content.lockDigestsReferencing(removed);
		this.candidates.deleteByArticles(removed);
		List<SectionCitations> sections = this.content.sectionsOfDigestsCiting(removed);
		Set<Long> doomed = RemovalFollowUp.sectionsToDelete(sections, Set.copyOf(removed));
		this.content.deleteItems(removed);
		this.content.deleteSections(doomed);
	}

}
