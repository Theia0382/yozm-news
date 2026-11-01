package net.yozmnews.platform;

import java.util.List;

/**
 * 남겨진 발행의 목록 — docs/specs/platform.md §3.
 *
 * @param publications 발행 시각 오름차순 앞쪽 최대 {@link EventPublicationAdministration#MAX_BATCH} 건
 * @param total 남겨진 발행의 전체 건수
 */
public record StrandedPublications(List<StrandedPublication> publications, int total) {

	public StrandedPublications {
		publications = List.copyOf(publications);
		if (total < publications.size()) {
			throw new IllegalArgumentException("전체 건수(" + total + ")가 실은 건수(" + publications.size() + ")보다 적다");
		}
	}

}
