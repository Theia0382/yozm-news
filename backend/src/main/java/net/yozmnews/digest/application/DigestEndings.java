package net.yozmnews.digest.application;

import java.util.Objects;

import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.domain.digest.Digest;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.FailureCauses;
import net.yozmnews.user.DigestCompleted;
import net.yozmnews.user.DigestFailed;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 끝낸 다이제스트 저장의 정본 — 끝난 행을 저장하고, 같은 트랜잭션에서 {@code user} 가 선언한 이벤트를 낸다 — 실패면
 * {@link DigestFailed}, 내용 있음 · 후보 없음이면 {@link DigestCompleted}.
 * docs/specs/digest.md §4
 * <p>
 * 다이제스트를 끝내는 길(생성 · 생성 작업의 시작 전 끝내기 · 멈춘 생성 거두기)이 모두 이것으로 저장한다 — 실패 알림이 빠지는 길이 없다.
 */
@Component
class DigestEndings {

	/** 트랜잭션 밖에서 불렀을 때의 예외 메시지 */
	static final String OUTSIDE_TRANSACTION = "끝낸 다이제스트를 트랜잭션 밖에서 저장하려 했다 — 실패 이벤트는 저장과 같은 트랜잭션에서 낸다";

	private final DigestRepository digests;

	private final ApplicationEventPublisher events;

	DigestEndings(DigestRepository digests, ApplicationEventPublisher events) {
		this.digests = Objects.requireNonNull(digests, "digests");
		this.events = Objects.requireNonNull(events, "events");
	}

	/**
	 * 끝낸 다이제스트를 저장한다 — 끝의 갈래에 맞는 이벤트를 하나 낸다.
	 * @throws IllegalArgumentException 끝나지 않은 다이제스트일 때
	 * @throws IllegalStateException 트랜잭션 밖에서 불렀을 때 — 아무것도 쓰지 않는다
	 */
	Digest save(Digest ended) {
		Objects.requireNonNull(ended, "ended");
		if (!ended.status().finished()) {
			throw new IllegalArgumentException("끝나지 않은 다이제스트다: " + ended.status());
		}
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(OUTSIDE_TRANSACTION);
		}
		Digest saved = this.digests.save(ended);
		switch (saved.status()) {
			case FAILED -> this.events.publishEvent(new DigestFailed(saved.userId(), saved.id().value(),
					saved.digestDate(), FailureCauses.of(saved.failure().orElseThrow()), saved.completedAt().orElseThrow()));
			case READY, EMPTY -> this.events.publishEvent(new DigestCompleted(saved.userId(), saved.id().value(),
					saved.digestDate(), saved.completedAt().orElseThrow()));
			case SCHEDULED, RUNNING -> throw new IllegalStateException("끝나지 않은 다이제스트를 저장했다: " + saved.status());
		}
		return saved;
	}

}
