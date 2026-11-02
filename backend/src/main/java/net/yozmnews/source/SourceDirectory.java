package net.yozmnews.source;

/**
 * 다른 모듈이 소스 · 피드가 아직 있는지 묻는 조회 — docs/specs/source.md §3.
 * <p>
 * 숨긴 소스 · 피드도 있다. 운영자가 지운 소스 · 피드만 없다. 호출자의 트랜잭션에서 읽는다.
 */
public interface SourceDirectory {

	/** 피드 행이 있는가 — 소스를 지우면 그 피드도 지워지므로 소스 삭제도 여기서 드러난다 */
	boolean feedExists(FeedId id);

}
