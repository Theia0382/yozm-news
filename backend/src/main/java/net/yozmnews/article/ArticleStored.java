package net.yozmnews.article;

import java.util.Objects;

/**
 * 새 기사를 저장했다 — article → evaluation · event. 기사 하나에 많아야 한 번 발행하고, 같은 기사로 판정된 항목이나 같은
 * 이벤트를 다시 처리한 경우에는 발행하지 않는다. 기사 내용은 싣지 않고 기사 ID 만 싣는다.
 *
 * @param articleId 새로 저장한 기사
 */
public record ArticleStored(ArticleId articleId) {

	public ArticleStored {
		Objects.requireNonNull(articleId, "articleId");
	}

}
