package net.yozmnews.platform.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.EventPublication;

/** 이벤트 발행 레지스트리의 행을 읽고 지운다 — 이벤트 내용은 읽을 수 있는지의 판정에만 읽고 싣지 않는다. docs/specs/platform.md §4 · platform-R15 · platform-R16 */
public interface RegistryRows {

	/**
	 * 레지스트리의 모든 행을 읽는다.
	 * @return 발행 시각 오름차순, 같으면 ID 순
	 */
	List<RegistryRow> findAll();

	/**
	 * 주어진 ID 의 행을 읽는다.
	 * @param ids 발행 ID
	 * @return 있는 행 — 없는 ID 는 빠진다
	 */
	List<RegistryRow> findByIds(Collection<UUID> ids);

	/**
	 * 행이 읽은 그대로(상태 · 처리 시도 · 이벤트 종류)일 때만 지운다.
	 * @param row 읽은 행 — 상태가 있어야 한다
	 * @return 지웠으면 참
	 */
	boolean deleteIfUnchanged(RegistryRow row);

	/**
	 * 레지스트리 행 하나.
	 *
	 * @param id 발행 ID
	 * @param eventType 이벤트 클래스 이름
	 * @param listenerId 리스너 ID
	 * @param publishedAt 발행 시각
	 * @param status 발행 상태 — 열이 비었으면 null
	 * @param completionAttempts 처리 시도 수 — 열이 비었으면 0
	 * @param lastResubmittedAt 마지막 재제출 시각
	 * @param eventReadable 이벤트를 읽을 수 있는지 — 클래스를 로드할 수 있고 본문을 그 클래스로 읽을 수 있다(platform-R16)
	 */
	record RegistryRow(UUID id, String eventType, String listenerId, Instant publishedAt,
			EventPublication.@Nullable Status status, int completionAttempts, @Nullable Instant lastResubmittedAt,
			boolean eventReadable) {

		public RegistryRow {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(eventType, "eventType");
			Objects.requireNonNull(listenerId, "listenerId");
			Objects.requireNonNull(publishedAt, "publishedAt");
		}

	}

}
