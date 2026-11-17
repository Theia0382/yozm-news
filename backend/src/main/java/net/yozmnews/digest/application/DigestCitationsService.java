package net.yozmnews.digest.application;

import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.content.CitationRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.feedback.DigestCitations;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code feedback} 이 선언한 인용 확인 {@link DigestCitations} 의 구현 — docs/specs/digest.md §4 · digest-R14. 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Service
class DigestCitationsService implements DigestCitations {

	private final CitationRepository citations;

	DigestCitationsService(CitationRepository citations) {
		this.citations = Objects.requireNonNull(citations, "citations");
	}

	@Override
	@Transactional(readOnly = true)
	public boolean cites(UserId user, long digestId, ArticleId article) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(article, "article");
		if (digestId < 1) {
			return false;
		}
		return this.citations.cited(user, new DigestId(digestId), article);
	}

}
