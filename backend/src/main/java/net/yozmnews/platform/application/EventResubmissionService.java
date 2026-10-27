package net.yozmnews.platform.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.yozmnews.platform.domain.EventResubmissionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Service;

/**
 * 미완료 이벤트 발행 가운데 정책이 고른 것을 다시 처리하게 한다. 미완료 발행 전체를 읽어 고르므로, 상한에 이른 실패
 * 발행이 쌓여도 새 실패 발행이 밀리지 않는다. 같은 발행은 여러 인스턴스가 동시에 불러도 한 곳에서만 재제출된다
 * (Modulith 의 조건부 상태 갱신).
 */
@Service
public class EventResubmissionService {

	private static final Logger logger = LoggerFactory.getLogger(EventResubmissionService.class);

	private final IncompleteEventPublications publications;

	private final EventResubmissionPolicy policy;

	EventResubmissionService(IncompleteEventPublications publications, EventResubmissionPolicy policy) {
		this.publications = publications;
		this.policy = policy;
	}

	/**
	 * 다시 처리할 발행을 재제출한다. 리스너는 비동기로 돌아 이 호출은 처리 끝을 기다리지 않는다. 상한에 이른 실패 발행이
	 * 남아 있으면 그 ID 를 경고 로그 한 줄로 남긴다 — 남아 있는 동안 호출마다 남는다.
	 */
	public void resubmit() {
		List<UUID> exhausted = new ArrayList<>();
		this.publications.resubmitIncompletePublications(publication -> switch (this.policy.decide(publication)) {
			case RESUBMIT -> true;
			case EXHAUSTED -> {
				exhausted.add(publication.getIdentifier());
				yield false;
			}
			case SKIP -> false;
		});
		if (!exhausted.isEmpty()) {
			logger.warn("재처리 상한({}회)에 이른 실패 이벤트 발행 {}건: {}", EventResubmissionPolicy.MAX_ATTEMPTS,
					exhausted.size(), exhausted);
		}
	}

}
