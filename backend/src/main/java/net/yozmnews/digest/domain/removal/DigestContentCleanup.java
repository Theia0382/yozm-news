package net.yozmnews.digest.domain.removal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventId;

/**
 * 지운 기사의 내용 행 정리 — 다이제스트 행 잠금 · 인용 · 섹션 · 지울 항목 섹션의 키 · docs/specs/digest.md §4 · digest-R10
 */
public interface DigestContentCleanup {

	/**
	 * 주어진 기사를 후보나 인용으로 가진 다이제스트 행을 ID 순으로 잠근다 — 트랜잭션 안에서 처음 부른다. 같은 다이제스트를 다루는 생성 ·
	 * 다른 지운 기사 따르기 · 보존 정리가 끝날 때까지 기다린다.
	 * @return 잠근 다이제스트 ID — 오름차순
	 */
	List<Long> lockDigestsReferencing(Collection<ArticleId> removed);

	/** 주어진 기사를 하나라도 인용한 다이제스트마다 그 다이제스트의 모든 섹션과 각 섹션이 인용한 기사 — 다이제스트 · 섹션 순서 */
	List<SectionCitations> sectionsOfDigestsCiting(Collection<ArticleId> removed);

	/**
	 * 주어진 섹션 가운데 항목 섹션(주요 뉴스 · 헤드라인)의 항목 키 — 섹션의 위치 0 사건. 섹션을 지우기 전에 읽는다.
	 * @return 다이제스트 ID → 그 다이제스트의 항목 키 — 사건 행이 없는 섹션은 빠진다
	 */
	Map<Long, List<EventId>> itemKeysOf(Collection<Long> sectionIds);

	/** 주어진 기사의 인용 행을 지운다 */
	void deleteItems(Collection<ArticleId> removed);

	/** 주어진 섹션과 그 인용 · 섹션 사건 행을 지운다 — 인용 · 섹션 사건 행은 섹션을 따라 지워진다 */
	void deleteSections(Collection<Long> sectionIds);

}
