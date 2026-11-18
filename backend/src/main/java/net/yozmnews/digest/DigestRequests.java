package net.yozmnews.digest;

import net.yozmnews.platform.UserId;

/**
 * 수동 다이제스트 생성 요청 — 사용자가 화면에서 다이제스트를 지금 만들어 달라고 한다. docs/specs/digest.md §3
 * <p>
 * 오늘(사용자 시간대) 날짜의 다이제스트가 있으면 그 날짜를 같은 기간으로 다시 만들고, 없으면 오늘 날짜로 요청 시각까지의 기간을 새로 만든다.
 * 사용자당 하루(사용자 시간대) 정한 횟수까지 받는다 — 그 사용자의 다이제스트가 생성 중이거나 쓸 모델이 없으면 받지 않고 세지 않는다. 받으면
 * 다이제스트 행을 만들고 생성 작업을 등록한 뒤 돌아간다 — 생성은 작업 서버가 한다. 사용자를 인자로 받는다 — 화면 경로에서는 세션 사용자로만
 * 부른다.
 */
public interface DigestRequests {

	/**
	 * 그 사용자의 수동 생성을 요청한다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 생성 작업은 행을 만든 트랜잭션이 커밋된 뒤 등록한다
	 */
	DigestRequestOutcome request(UserId user);

}
