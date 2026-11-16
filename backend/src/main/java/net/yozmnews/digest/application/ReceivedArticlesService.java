package net.yozmnews.digest.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.ReceivedArticles;
import net.yozmnews.digest.domain.receipt.ReceiptRepository;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 조회 {@link ReceivedArticles} 의 구현 — docs/specs/digest.md §4 · digest-R13. 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class ReceivedArticlesService implements ReceivedArticles {

	private final ReceiptRepository receipts;

	ReceivedArticlesService(ReceiptRepository receipts) {
		this.receipts = Objects.requireNonNull(receipts, "receipts");
	}

	@Override
	@Transactional(readOnly = true)
	public Map<ArticleId, LocalDate> receivedAmong(UserId user, Collection<ArticleId> articles) {
		Objects.requireNonNull(user, "user");
		long distinct = articles.stream().distinct().count();
		if (distinct > MAX_ARTICLES) {
			throw new IllegalArgumentException("한 번에 묻는 기사는 " + MAX_ARTICLES + "개까지다: " + distinct);
		}
		return this.receipts.firstShownDates(user, articles);
	}

}
