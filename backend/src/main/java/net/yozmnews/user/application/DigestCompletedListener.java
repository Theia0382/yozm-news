package net.yozmnews.user.application;

import java.util.Objects;

import net.yozmnews.user.DigestCompleted;
import net.yozmnews.user.domain.summary.SummaryJobQueue;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@link DigestCompleted} 리스너 — 그 사용자의 사용자 스토리라인 정리 작업을 등록한다. docs/specs/user.md §4
 * <p>
 * 역할 애너테이션이 없어 두 역할 모두에서 켜진다 — 리스너는 발행한 프로세스와 발행을 재제출한 프로세스에서 돈다(platform-R9). 다시 처리해도
 * 작업은 하나다 — 작업 ID 가 (사용자, 다이제스트)에서 나온다.
 */
@Component
class DigestCompletedListener {

	private final SummaryJobQueue queue;

	DigestCompletedListener(SummaryJobQueue queue) {
		this.queue = Objects.requireNonNull(queue, "queue");
	}

	@ApplicationModuleListener
	public void on(DigestCompleted completed) {
		this.queue.enqueue(completed.user(), completed.digestId());
	}

}
