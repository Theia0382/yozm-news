package net.yozmnews.digest.domain.content;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.platform.UserId;

/**
 * 인용 확인 저장소 — docs/specs/digest.md §4 · digest-R14.
 */
public interface CitationRepository {

	/**
	 * 그 사용자의 그 다이제스트가 그 기사를 인용했는지 읽는다. 다이제스트의 상태 · 날짜를 보지 않는다 — 날짜별로 보이는 다이제스트에 덮인 다이제스트의 인용도
	 * 참이다. 없는 다이제스트 · 다른 사용자의 다이제스트 · 인용하지 않은 기사는 거짓이다.
	 */
	boolean cited(UserId user, DigestId digest, ArticleId article);

}
