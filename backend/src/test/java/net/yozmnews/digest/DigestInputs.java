package net.yozmnews.digest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.input.CandidateArticle;
import net.yozmnews.digest.domain.input.DigestInput;
import net.yozmnews.digest.domain.input.DigestProfile;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.user.SummaryLength;

/**
 * 생성 흐름 시험의 입력 — 후보만 시험이 정하고 프롬프트 재료는 고정 값이다(제목 "시험 기사 {기사 ID}", 요약문 · 출처 없음, 프로필 정치 · 서울 ·
 * 보통). 생성 흐름이 재료를 읽지 않는 시험(후보 행 · 상태 · 인용)에서 쓴다.
 */
public final class DigestInputs {

	/** 고정 프로필 */
	public static final DigestProfile PROFILE = new DigestProfile(List.of("정치"), List.of(), "",
			ZoneId.of("Asia/Seoul"), SummaryLength.NORMAL, ReasoningEffort.MEDIUM);

	private DigestInputs() {
	}

	/** 후보를 그 순서로 담은 입력 — 묶음 없음 */
	public static DigestInput of(List<DigestCandidate> candidates) {
		return new DigestInput(PROFILE,
				candidates.stream()
					.map(candidate -> new CandidateArticle(candidate, "시험 기사 " + candidate.articleId().value(), null,
							List.of(), Instant.parse("2026-11-15T00:00:00Z"), List.of(), List.of()))
					.toList(),
				List.of());
	}

}
