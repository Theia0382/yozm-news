package net.yozmnews.article;

import java.util.Collection;
import java.util.Map;

/**
 * 다른 모듈이 기사를 읽는 조회 — docs/specs/article.md §3.
 * <p>
 * 트랜잭션을 열지 않는다. 각 조회는 그 순간의 값이다.
 */
public interface ArticleDirectory {

	/**
	 * 기사들의 제목 · 요약문 · 엔트리를 읽는다.
	 * @return 기사 ID → 내용 — 없는(지운) 기사는 결과에 없다. 빈 입력이면 빈 맵
	 */
	Map<ArticleId, ArticleContent> findAll(Collection<ArticleId> ids);

}
