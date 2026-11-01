package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.EventPublicationAdministration;
import net.yozmnews.platform.StrandedPublication;
import net.yozmnews.platform.StrandedPublications;
import net.yozmnews.platform.StrandedReason;
import org.jspecify.annotations.Nullable;

/**
 * 남겨진 이벤트 발행 목록 — 발행 시각 오름차순 앞쪽 최대 {@code limit} 건, 전체 건수, 실을 수 있는 건수. docs/specs/admin.md §3
 */
record StrandedPublicationListResponse(List<StrandedPublicationResponse> publications, int total, int limit) {

	static StrandedPublicationListResponse of(StrandedPublications stranded) {
		return new StrandedPublicationListResponse(
				stranded.publications().stream().map(StrandedPublicationResponse::of).toList(), stranded.total(),
				EventPublicationAdministration.MAX_BATCH);
	}

	/** 남겨진 발행 하나 — ID 는 UUID 문자열 */
	record StrandedPublicationResponse(String id, StrandedReason reason, String eventType, String listenerId,
			Instant publishedAt, int completionAttempts, @Nullable Instant lastResubmittedAt) {

		static StrandedPublicationResponse of(StrandedPublication publication) {
			return new StrandedPublicationResponse(publication.id().toString(), publication.reason(),
					publication.eventType(), publication.listenerId(), publication.publishedAt(),
					publication.completionAttempts(), publication.lastResubmittedAt());
		}

	}

}
