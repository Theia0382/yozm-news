package net.yozmnews.article.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.source.FeedId;
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

	/**
	 * 소스의 엔트리를 모두 지운다.
	 * @return 지운 엔트리의 기사 — 같은 기사가 여럿일 수 있다
	 */
	List<ArticleId> deleteEntries(SourceId sourceId);

	/**
	 * 소스의 한 피드의 엔트리를 지운다.
	 * @return 지운 엔트리의 기사
	 */
	List<ArticleId> deleteEntries(SourceId sourceId, FeedId feedId);

	/**
	 * 주어진 기사 가운데 엔트리가 남지 않은 기사를 지운다 — 다른 소스 · 피드의 엔트리가 남은 기사는 남긴다.
	 * @return 지운 기사 — ID 순, 중복 없음
	 */
	List<ArticleId> deleteArticlesWithoutEntries(Collection<ArticleId> articleIds);

	/**
	 * 기사들의 제목 · 요약문 · 엔트리를 읽는다.
	 * @return 기사 ID → 내용 — 없는 기사는 빠진다. 엔트리는 받은 시각 순, 같으면 피드 ID 순
	 */
	Map<ArticleId, ArticleContent> findContents(Collection<ArticleId> articleIds);

	/** 엔트리가 모두 그 소스의 것인 기사 수 — 소스를 지우면 함께 지워질 기사 */
	long countArticlesOnlyIn(SourceId sourceId);

	/** 엔트리가 모두 그 소스의 그 피드의 것인 기사 수 — 피드를 지우면 함께 지워질 기사 */
	long countArticlesOnlyIn(SourceId sourceId, FeedId feedId);

}
