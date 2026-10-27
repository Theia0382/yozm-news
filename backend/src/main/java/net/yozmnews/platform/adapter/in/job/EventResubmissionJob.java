package net.yozmnews.platform.adapter.in.job;

import net.yozmnews.platform.application.EventResubmissionService;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

/**
 * 실패한 이벤트 발행을 주기적으로 다시 처리하는 반복 작업. JobRunr 가 다른 패키지에서 반사로 부르므로 클래스가 public
 * 이어야 한다.
 */
@Component
public class EventResubmissionJob {

	static final String ID = "event-publication-resubmission";

	private final EventResubmissionService service;

	EventResubmissionJob(EventResubmissionService service) {
		this.service = service;
	}

	/**
	 * 재시도하지 않는다 — 실패한 회차가 재시도를 기다리는 동안 JobRunr 는 이 작업의 다음 회차를 건너뛰므로, 다음 회차가 곧
	 * 재시도다.
	 */
	@Recurring(id = ID, interval = "${yozmnews.events.resubmission-interval}")
	@Job(name = "실패한 이벤트 발행 재처리", retries = 0)
	public void run() {
		this.service.resubmit();
	}

}
