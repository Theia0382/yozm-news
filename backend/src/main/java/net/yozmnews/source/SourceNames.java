package net.yozmnews.source;

import java.util.List;

/**
 * 다른 모듈이 사용자가 고를 소스 목록을 읽는 조회 — {@code user} 의 제외 소스가 쓴다 — docs/specs/source.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code SourceNameService} 다. 이름의 해석은 운영 목록과 같다(source-R57). 읽기 전용 트랜잭션 하나에서
 * 읽는다.
 */
public interface SourceNames {

	/** 숨기지 않은 소스 전부(켜짐 무관) — 소스 ID 순. 숨긴 소스(source-R53) · 지운 소스는 없다 */
	List<SourceName> visible();

}
