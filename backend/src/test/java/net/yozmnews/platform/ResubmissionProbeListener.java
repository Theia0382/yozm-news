package net.yozmnews.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 재처리 시험용 리스너 — 이벤트 발행 레지스트리에 기록되는 비동기 리스너다. */
@Component
public class ResubmissionProbeListener {

	private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

	@ApplicationModuleListener
	public void on(ResubmissionProbeEvent event) {
		int call = this.calls.computeIfAbsent(event.id(), id -> new AtomicInteger()).incrementAndGet();
		switch (event.behavior()) {
			case SUCCEED -> {
			}
			case FAIL_ONCE -> {
				if (call == 1) {
					throw new IllegalStateException("시험용 첫 처리 실패: " + event.id());
				}
			}
			case ALWAYS_FAIL -> throw new IllegalStateException("시험용 처리 실패: " + event.id());
		}
	}

	public int calls(String id) {
		AtomicInteger count = this.calls.get(id);
		return (count != null) ? count.get() : 0;
	}

}
