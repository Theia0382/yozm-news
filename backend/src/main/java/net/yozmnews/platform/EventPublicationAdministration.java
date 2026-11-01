package net.yozmnews.platform;

import java.util.Set;
import java.util.UUID;

/**
 * 이벤트 발행 운영 기능 — 운영자 화면이 부르는 공개 API. docs/specs/platform.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code EventPublicationAdministrationService} 다.
 */
public interface EventPublicationAdministration {

	/** 다시 처리 · 폐기 한 번에 받는 발행 수의 상한이자 목록에 싣는 발행 수의 상한 */
	int MAX_BATCH = 500;

	/** 남겨진 발행 — 발행 시각 오름차순 앞쪽 최대 {@link #MAX_BATCH} 건과 전체 건수 */
	StrandedPublications strandedPublications();

	/**
	 * 주어진 발행 중 상한에 이른 것을 지금 재제출한다(처리 시도 +1). 발행마다 결과를 낸다.
	 * @param ids 다시 처리할 발행 ID
	 * @return 발행마다의 결과, {@link #MAX_BATCH} 를 넘으면 아무것도 하지 않고 {@link BatchResult.OverLimit}
	 */
	BatchResult<ResubmissionResult> resubmit(Set<UUID> ids);

	/**
	 * 주어진 발행 중 남겨진 발행을 읽은 상태 그대로일 때 지운다. 발행마다 결과를 낸다.
	 * @param ids 폐기할 발행 ID
	 * @return 발행마다의 결과, {@link #MAX_BATCH} 를 넘으면 아무것도 하지 않고 {@link BatchResult.OverLimit}
	 */
	BatchResult<DiscardResult> discard(Set<UUID> ids);

}
