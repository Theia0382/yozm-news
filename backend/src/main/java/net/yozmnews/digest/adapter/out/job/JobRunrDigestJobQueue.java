package net.yozmnews.digest.adapter.out.job;

import java.util.Objects;
import java.util.UUID;

import net.yozmnews.digest.adapter.in.job.GenerateDigestJob;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestJobQueue;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

/**
 * {@link DigestJobQueue} 의 JobRunr 구현 — 토큰을 작업 ID 로 {@link GenerateDigestJob} 을 등록한다. docs/specs/digest.md §4
 * <p>
 * 역할 애너테이션이 없다 — 작업 등록은 두 역할 공통이고(수동 생성은 화면 역할에서 등록한다), 등록은 작업의 타입 · 메서드만 적으므로 화면 역할에 그
 * 작업의 빈이 없어도 된다. 같은 ID 의 작업이 저장소에 있으면 JobRunr 가 등록을 건너뛴다.
 */
@Component
class JobRunrDigestJobQueue implements DigestJobQueue {

	private final JobScheduler jobs;

	JobRunrDigestJobQueue(JobScheduler jobs) {
		this.jobs = Objects.requireNonNull(jobs, "jobs");
	}

	@Override
	public void enqueue(DigestId id, UUID token) {
		long digestId = id.value();
		this.jobs.<GenerateDigestJob>enqueue(token, job -> job.run(digestId, token));
	}

}
