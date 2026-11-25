package net.yozmnews.report;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 리포트 다시 만들기 — 실패한 리포트 하나를 같은 기간으로 다시 생성을 기다리게 하고 생성 작업을 등록한다. 독자 화면의 처리기가 세션 사용자로
 * 부른다. docs/specs/report.md §3
 * <p>
 * 트랜잭션 밖에서 부른다 — 커밋 뒤 생성 작업을 등록한다.
 */
public interface ReportRequests {

	/**
	 * @return 다시 만들기의 결과 — 그 사용자의 리포트가 아니거나 없으면 빈 값
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 아무것도 하지 않는다
	 */
	Optional<ReportRegenerationOutcome> regenerate(UserId user, ReportId id);

}
