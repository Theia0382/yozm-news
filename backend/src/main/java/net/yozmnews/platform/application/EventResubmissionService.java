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
 * 이벤트 발행 재처리 유스케이스 — docs/specs/platform.md §4 · platform-R1 · platform-R4
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
