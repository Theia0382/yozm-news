package net.yozmnews.article.domain;

import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.source.SourceId;

/**
 * 기사 저장소 — docs/specs/article.md §4 · article-R7 · article-R8 · article-R9
 */
public interface ArticleRepository {

	/** 이 트랜잭션이 끝날 때까지 같은 소스의 판정을 직렬화한다 — 트랜잭션 안에서만 부른다 */
	void lockSource(SourceId sourceId);

	MatchCandidates findCandidates(IncomingItem item);

	/**
	 * 새 기사를 넣는다.
	 * @return 넣은 기사 — 같은 정규 URL 의 기사가 이미 있으면(다른 소스가 먼저 저장) 비어 있다
	 */
	Optional<ArticleId> insertIfAbsent(IncomingItem item);

	/** 정규 URL 해시로 기사를 찾는다 — {@link #insertIfAbsent} 가 비었을 때 다시 읽는다 */
	Optional<ArticleId> findByUrlHash(KeyHash urlHash);

	/**
	 * 그 피드의 항목을 기사의 엔트리로 기록한다.
	 * @return 새로 기록했으면 true, 그 기사 · 피드의 엔트리가 이미 있으면 false
	 */
	boolean insertEntryIfAbsent(ArticleId articleId, IncomingItem item, MatchKind kind);

}
