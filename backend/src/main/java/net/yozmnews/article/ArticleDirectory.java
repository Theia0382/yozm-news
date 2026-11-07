package net.yozmnews.article;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.source.SourceId;

/**
 * 다른 모듈이 기사를 읽는 조회 — docs/specs/article.md §3.
 * <p>
 * 트랜잭션을 열지 않는다. 각 조회는 그 순간의 값이다.
 */
public interface ArticleDirectory {

	/**
	 * 기사들의 제목 · 요약문 · 정규 URL · 발행 시각 · 엔트리를 읽는다.
	 * @return 기사 ID → 내용 — 없는(지운) 기사는 결과에 없다. 빈 입력이면 빈 맵
	 */
	Map<ArticleId, ArticleContent> findAll(Collection<ArticleId> ids);

	/**
	 * 처음 본 시각이 {@code since} 이상인 기사를 찾는다 — 체계를 바꿀 때의 재평가 대상(evaluation-R25) · 사건 묶기 채우기 대상(event-R19).
	 * <p>
	 * 처음 본 시각에는 인덱스가 없어 표 전체를 읽는다 — 드문 운영 연산이 부른다.
	 * @return 기사 ID — ID 순
	 */
	List<ArticleId> firstSeenSince(Instant since);

	/**
	 * 기사들의 엔트리 소스를 읽는다 — 사건 묶기가 큰 사건의 서로 다른 소스 수를 센다(event-R15).
	 * @return 기사 ID → 그 기사의 엔트리가 가진 소스 — 엔트리가 없는(지운) 기사는 빠진다. 빈 입력이면 빈 맵
	 */
	Map<ArticleId, Set<SourceId>> sources(Collection<ArticleId> ids);

}
