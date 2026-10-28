package net.yozmnews.article.domain;

import java.util.Optional;

import net.yozmnews.article.ArticleId;
import net.yozmnews.source.SourceId;

/**
 * 기사 저장소 — 같은 기사 판정의 후보 조회와 기록. 기사는 처음 저장한 값이 남고 갱신하지 않는다.
 */
public interface ArticleRepository {

	/** 이 트랜잭션이 끝날 때까지 같은 소스의 판정을 직렬화한다 — 트랜잭션 안에서만 부른다 */
	void lockSource(SourceId sourceId);

	MatchCandidates findCandidates(IncomingItem item);

	/** 새 기사 — 같은 정규 URL 의 기사가 이미 있으면(다른 소스가 먼저 저장) 비어 있다 */
	Optional<ArticleId> insertIfAbsent(IncomingItem item);

	/** 정규 URL 해시로 기사 — {@link #insertIfAbsent} 가 비었을 때 다시 읽는다 */
	Optional<ArticleId> findByUrlHash(KeyHash urlHash);

	/**
	 * 그 피드의 항목을 기사에 기록한다 — 기사 · 피드마다 한 번이고, 이미 있으면 아무것도 바꾸지 않는다.
	 * @return 새로 기록했는지
	 */
	boolean insertEntryIfAbsent(ArticleId articleId, IncomingItem item, MatchKind kind);

}
