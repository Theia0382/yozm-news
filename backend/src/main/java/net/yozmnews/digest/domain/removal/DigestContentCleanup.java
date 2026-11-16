package net.yozmnews.digest.domain.removal;

import java.util.Collection;
import java.util.List;

import net.yozmnews.article.ArticleId;

/**
 * 지운 기사의 내용 행 정리 — 다이제스트 행 잠금 · 인용 · 섹션 · docs/specs/digest.md §4 · digest-R10
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

	/** 주어진 기사의 인용 행을 지운다 */
	void deleteItems(Collection<ArticleId> removed);

	/** 주어진 섹션과 그 인용 행을 지운다 */
	void deleteSections(Collection<Long> sectionIds);

}
