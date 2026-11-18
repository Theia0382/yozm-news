package net.yozmnews.digest.domain.view;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestStatus;
import org.jspecify.annotations.Nullable;

/**
 * 날짜 보기의 재료 — 다이제스트 행 한 줄. docs/specs/digest.md §4 · digest-R38
 *
 * @param id 다이제스트 ID
 * @param status 상태
 * @param periodStart 기간 시작
 * @param periodEnd 기간 끝
 * @param provider 시작할 때 적은 공급자 키 — 시작 전이면 null
 * @param model 시작할 때 적은 모델 ID — 시작 전이면 null
 * @param candidateCount 시작할 때 받은 후보 수 — 시작 전이면 null
 * @param inputTokens 입력 토큰 — 공급자가 답하지 않았으면 null
 * @param outputTokens 출력 토큰 — 공급자가 답하지 않았으면 null
 * @param failure 실패 코드 — {@code FAILED} 일 때만
 * @param completedAt 끝낸 시각 — 끝나기 전이면 null
 * @param shown 그 날짜의 보이는 다이제스트인가(digest-R13 — 판정의 정본은 저장소 구현의 질의)
 */
public record DigestDayRow(long id, DigestStatus status, Instant periodStart, Instant periodEnd,
		@Nullable String provider, @Nullable String model, @Nullable Integer candidateCount,
		@Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable DigestFailure failure,
		@Nullable Instant completedAt, boolean shown) {

	public DigestDayRow {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(periodStart, "periodStart");
		Objects.requireNonNull(periodEnd, "periodEnd");
	}

}
