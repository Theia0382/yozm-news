package net.yozmnews.event.domain.assignment;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * 판정에 쓰는 후보 사건 하나 — 저장 기준 이상인 후보이고, 배정과 함께 남아 재판정의 재료가 되고 병합이 앞선 배정 확률로 읽는다.
 *
 * @param score 산식의 점수 — {@link CandidateScoring}
 * @param probability 사건 소속 판정의 확률 — 묻지 않았으면 null
 */
public record StoredCandidate(long eventId, double score, @Nullable Double probability) {

	/**
	 * 같은 사건이 둘 이상인 후보를 하나로 모은다 — 합쳐진 사건을 남는 사건으로 읽으면 생긴다(event-R18). 점수는 큰 값, 확률은 큰 값이고 확률이
	 * 없는 쪽은 있는 쪽에 진다. 모으기 규칙의 정본이다.
	 * @return 사건 ID 순
	 */
	public static List<StoredCandidate> folded(List<StoredCandidate> candidates) {
		Map<Long, StoredCandidate> byEvent = new TreeMap<>();
		for (StoredCandidate candidate : candidates) {
			byEvent.merge(candidate.eventId(), candidate, StoredCandidate::fold);
		}
		return List.copyOf(byEvent.values());
	}

	private static StoredCandidate fold(StoredCandidate a, StoredCandidate b) {
		@Nullable Double pa = a.probability();
		@Nullable Double pb = b.probability();
		@Nullable Double probability = (pa == null) ? pb : (pb == null) ? pa : Double.valueOf(Math.max(pa, pb));
		return new StoredCandidate(a.eventId(), Math.max(a.score(), b.score()), probability);
	}

}
