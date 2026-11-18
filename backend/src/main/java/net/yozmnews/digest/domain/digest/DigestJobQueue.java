package net.yozmnews.digest.domain.digest;

import java.util.UUID;

/**
 * 다이제스트 생성 작업의 등록 포트 — 작업 서버(외부 의존성)에 생성 작업을 넣는다. 구현은 {@code adapter/out/job} 의
 * {@code JobRunrDigestJobQueue}. docs/specs/digest.md §4
 */
public interface DigestJobQueue {

	/** 그 다이제스트의 생성 작업을 토큰을 작업 ID 로 등록한다 — 같은 토큰을 거듭 등록해도 작업은 하나다 */
	void enqueue(DigestId id, UUID token);

}
