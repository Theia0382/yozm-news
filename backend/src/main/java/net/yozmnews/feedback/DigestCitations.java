package net.yozmnews.feedback;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.UserId;

/**
 * 다이제스트의 인용 확인 — 이 모듈이 선언하고 {@code digest} 가 구현한다. docs/specs/feedback.md §3 · feedback-R2.
 * <p>
 * 이 모듈은 {@code digest} 의 타입을 쓰지 않고 다이제스트를 ID 로만 든다. 부르는 쪽의 트랜잭션에 들고 쓰지 않는다.
 */
public interface DigestCitations {

	/**
	 * 그 다이제스트가 그 사용자의 것이고 그 다이제스트의 인용에 그 기사가 있는지 답한다. 없는 다이제스트 · 다른 사용자의 다이제스트 · 인용하지 않은 기사는
	 * 거짓이다. 날짜별로 보이는 다이제스트인지는 보지 않는다.
	 */
	boolean cites(UserId user, long digestId, ArticleId article);

}
