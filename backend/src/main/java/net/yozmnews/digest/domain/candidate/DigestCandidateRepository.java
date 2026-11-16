package net.yozmnews.digest.domain.candidate;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.digest.DigestId;

/**
 * 후보 행 저장소 — 후보 행은 생성 중 그 기사를 인용해도 되는지의 표시다 · docs/specs/digest.md §4 · digest-R9 · digest-R10 ·
 * digest-R11
 */
public interface DigestCandidateRepository {

	/** 그 다이제스트의 후보 행을 지우고 다시 쓴다 — 그 다이제스트 행을 잠근 트랜잭션 안에서 부른다 */
	void replace(DigestId digest, CandidateSet candidates);

	/**
	 * 그 다이제스트의 후보 행을 공유 잠금하고 읽는다 — 그 다이제스트 행을 잠근 트랜잭션 안에서 부른다.
	 * @return 남은 후보 기사 — 지운 기사 따르기가 먼저 지운 행은 없다
	 */
	Set<ArticleId> lockArticles(DigestId digest);

	/** 그 다이제스트의 주어진 기사 행을 지운다 — 그 다이제스트 행을 잠근 트랜잭션 안에서 부른다 */
	void delete(DigestId digest, Collection<ArticleId> articles);

	/** 주어진 기사의 후보 행을 모든 다이제스트에서 지운다 — 그 다이제스트 행들을 잠근 트랜잭션 안에서 부른다 */
	void deleteByArticles(Collection<ArticleId> articles);

	/**
	 * 이 시각보다 먼저 만든 다이제스트의 후보 행을 지운다 — 후보 행이 남은 그 다이제스트 행을 ID 순으로 잠근 뒤 지운다. 트랜잭션 안에서
	 * 부른다.
	 * @return 지운 행 수
	 */
	int deleteOfDigestsCreatedBefore(Instant cutoff);

}
