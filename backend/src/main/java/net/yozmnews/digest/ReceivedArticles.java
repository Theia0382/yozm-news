package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.UserId;

/**
 * 받은 소식 — 사용자가 날짜별로 보이는 다이제스트에서 받은 기사와 받은 날짜. docs/specs/digest.md §3 · digest-R13.
 * <p>
 * 읽기 전용 트랜잭션 하나 · 질의 하나다. 다른 사용자의 다이제스트는 보지 않는다.
 */
public interface ReceivedArticles {

	/** 한 번에 묻는 서로 다른 기사 수의 상한 — 정본. 측정 전 목표 */
	int MAX_ARTICLES = 1000;

	/**
	 * 주어진 기사 가운데 그 사용자가 받은 기사와 받은 날짜를 읽는다.
	 * @return 받은 기사 → 받은 날짜(그 기사를 인용한, 날짜별로 보이는 다이제스트들의 날짜 가운데 가장 이른 것). 받지 않은 기사는 빠진다. 빈
	 *         입력이면 빈 맵
	 * @throws IllegalArgumentException 서로 다른 기사가 {@link #MAX_ARTICLES} 개를 넘을 때
	 */
	Map<ArticleId, LocalDate> receivedAmong(UserId user, Collection<ArticleId> articles);

}
