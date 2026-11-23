package net.yozmnews.user.adapter.in.job;

import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WorkerRole;
import net.yozmnews.user.application.StorylineSummaryRun;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

/**
 * 사용자 스토리라인 정리 작업 — 사용자 하나의 정리 실행을 돌린다. 작업 ID 는 (사용자, 계기가 된 다이제스트)에서 낸다. docs/specs/user.md §4
 * <p>
 * 재시도하지 않는다 — 다음 다이제스트가 다시 계기가 되고, 같은 실행을 거듭하면 사용자 토큰을 다시 쓴다. 다이제스트 ID 는 작업 ID 와 작업 이름에만
 * 쓰고 판단에 쓰지 않는다. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@WorkerRole
@Component
public class SummarizeStorylinesJob {

	private final StorylineSummaryRun run;

	SummarizeStorylinesJob(StorylineSummaryRun run) {
		this.run = run;
	}

	@Job(name = "사용자 스토리라인 정리 %0 (다이제스트 %1)", retries = 0)
	public void run(long userId, long digestId) {
		this.run.run(new UserId(userId));
	}

}
