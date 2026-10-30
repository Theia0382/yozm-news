package net.yozmnews.platform.adapter.in.job;

import net.yozmnews.platform.application.EventResubmissionService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 이벤트 발행 재처리 반복 작업 — docs/specs/platform.md §4 · platform-R1 · platform-R4.
 * <p>
 * JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public 이어야 한다.
 */
@Component
public class EventResubmissionJob {

	static final String ID = "event-publication-resubmission";

	private final EventResubmissionService service;

	EventResubmissionJob(EventResubmissionService service) {
		this.service = service;
	}

	@Recurring(id = ID, interval = "${yozmnews.events.resubmission-interval}")
	@Job(name = "실패한 이벤트 발행 재처리", retries = 0)
	public void run() {
		this.service.resubmit();
	}

}
