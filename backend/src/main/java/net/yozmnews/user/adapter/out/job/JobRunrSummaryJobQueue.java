package net.yozmnews.user.adapter.out.job;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.adapter.in.job.SummarizeStorylinesJob;
import net.yozmnews.user.domain.summary.SummaryJobQueue;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

/**
 * {@link SummaryJobQueue} 의 JobRunr 구현 — (사용자, 다이제스트)에서 낸 이름 기반 UUID 를 작업 ID 로 {@link SummarizeStorylinesJob} 을
 * 등록한다. docs/specs/user.md §4
 * <p>
 * 역할 애너테이션이 없다 — 등록은 이벤트 리스너가 하고 리스너는 두 역할 공통이다. 등록은 작업의 타입 · 메서드만 적으므로 화면 역할에 그 작업의 빈이
 * 없어도 된다. 같은 ID 의 작업이 저장소에 있으면 JobRunr 가 등록을 건너뛴다.
 */
@Component
class JobRunrSummaryJobQueue implements SummaryJobQueue {

	private final JobScheduler jobs;

	JobRunrSummaryJobQueue(JobScheduler jobs) {
		this.jobs = Objects.requireNonNull(jobs, "jobs");
	}

	@Override
	public void enqueue(UserId user, long digestId) {
		Objects.requireNonNull(user, "user");
		long userId = user.value();
		this.jobs.<SummarizeStorylinesJob>enqueue(jobId(userId, digestId), job -> job.run(userId, digestId));
	}

	/** 작업 ID — 같은 사용자 · 다이제스트면 같은 값 */
	static UUID jobId(long userId, long digestId) {
		return UUID.nameUUIDFromBytes(
				("user-storyline-summary:" + userId + ":" + digestId).getBytes(StandardCharsets.UTF_8));
	}

}
