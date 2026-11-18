package net.yozmnews.digest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 날짜의 보이는 다이제스트(digest-R13) — 그 날짜의 {@code READY} 가운데 ID 가 가장 큰 것. docs/specs/digest.md §3 · digest-R38
 *
 * @param digestId 다이제스트 ID
 * @param periodStart 기간 시작
 * @param periodEnd 기간 끝(제외)
 * @param provider 쓴 모델의 공급자 키
 * @param model 쓴 모델 ID
 * @param candidateCount 생성에 넘긴 후보 수
 * @param inputTokens 입력 토큰
 * @param outputTokens 출력 토큰
 * @param completedAt 끝낸 시각
 * @param sections 섹션 — 위치 순. 인용한 기사가 모두 지워졌으면 비어 있다(digest-R10)
 */
public record ShownDigest(long digestId, Instant periodStart, Instant periodEnd, String provider, String model,
		int candidateCount, int inputTokens, int outputTokens, Instant completedAt, List<DigestSectionView> sections) {

	public ShownDigest {
		Objects.requireNonNull(periodStart, "periodStart");
		Objects.requireNonNull(periodEnd, "periodEnd");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(completedAt, "completedAt");
		sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
	}

}
