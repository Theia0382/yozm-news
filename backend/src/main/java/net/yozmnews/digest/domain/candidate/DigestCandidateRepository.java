package net.yozmnews.digest.domain.candidate;

import java.util.Collection;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.digest.DigestId;

/**
 * 후보 행 저장소 — 후보 행은 생성 중 그 기사를 인용해도 되는지의 표시다 · docs/specs/digest.md §4 · digest-R9
 */
public interface DigestCandidateRepository {

	/** 그 다이제스트의 후보 행을 지우고 다시 쓴다 */
	void replace(DigestId digest, CandidateSet candidates);

	/**
	 * 그 다이제스트의 후보 행을 공유 잠금하고 읽는다 — 트랜잭션 안에서 부른다.
	 * @return 남은 후보 기사 — 지운 기사 따르기가 먼저 지운 행은 없다
	 */
	Set<ArticleId> lockArticles(DigestId digest);

	/** 그 다이제스트의 주어진 기사 행을 지운다 */
	void delete(DigestId digest, Collection<ArticleId> articles);

}
