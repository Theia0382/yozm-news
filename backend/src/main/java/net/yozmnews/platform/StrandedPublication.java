package net.yozmnews.platform;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * 남겨진 발행 하나 — docs/specs/platform.md §3.
 *
 * @param id 발행 ID
 * @param reason 남겨진 까닭
 * @param eventType 이벤트 클래스 이름
 * @param listenerId 리스너 ID
 * @param publishedAt 발행 시각
 * @param completionAttempts 처리 시도 수
 * @param lastResubmittedAt 마지막 재제출 시각 — 저장 때 발행 시각으로 채워지므로 재제출된 적 없으면 발행 시각과 같다
 */
public record StrandedPublication(UUID id, StrandedReason reason, String eventType, String listenerId,
		Instant publishedAt, int completionAttempts, @Nullable Instant lastResubmittedAt) {

	public StrandedPublication {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(listenerId, "listenerId");
		Objects.requireNonNull(publishedAt, "publishedAt");
	}

}
