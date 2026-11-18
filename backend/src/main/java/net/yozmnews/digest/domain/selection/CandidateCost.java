package net.yozmnews.digest.domain.selection;

import java.util.List;

/**
 * 입력 토큰의 추정 — 정본. 프롬프트 구성은 이 추정을 따른다 — docs/specs/digest.md §5
 * <p>
 * 보내기 전에는 공급자가 세는 토큰 수를 알 수 없어 코드 포인트로 어림한다. 한국어 기사 문장은 공급자 토크나이저에서 대략 코드 포인트 하나에 토큰
 * 1 개 이하라 {@link #TOKENS_PER_CODE_POINT} 를 1.0 으로 두어 넉넉히 센다. 상수는 모두 측정 전 목표다 — 다이제스트에 기록되는 실제 입력
 * 토큰(digest-R1)으로 고친다.
 */
public final class CandidateCost {

	/** 코드 포인트 하나의 토큰 어림 */
	public static final double TOKENS_PER_CODE_POINT = 1.0;

	/** 후보 줄 하나에서 제목 · 요약문 밖의 몫(코드 포인트) — 별칭 · 출처 · 발행 시각 · 주제 · 지역 태그 · 구분 기호 */
	public static final int CANDIDATE_OVERHEAD = 80;

	/** 지시 구획(역할 · 규칙 · 출력 스키마)의 몫(토큰) — 모델 예산에서 먼저 뺀다 */
	public static final int INSTRUCTION_RESERVE = 2_000;

	/** 사용자 프로필 구획에서 구독 주제 · 지역 선호 하나의 몫(코드 포인트) — 이름과 구분 기호 */
	public static final int PROFILE_TAG_COST = 20;

	/**
	 * 사용자 구획의 고정 틀(코드 포인트) — 문서 · 구획 키 · 괄호 · 시간대 ID. 가장 긴 IANA 시간대 ID 를 덮는다 — 구독 주제 · 지역 선호 ·
	 * 관심사 · 묶음 · 후보 줄 밖의 글이다
	 */
	public static final int PROFILE_OVERHEAD = 200;

	/**
	 * 큰 스토리라인 묶음 하나에서 이름 밖의 몫(코드 포인트) — 묶음 별칭 · 구분 기호 · 묶음 기사 별칭들(행마다 최대
	 * {@link SelectionRules#BIG_EVENTS_PER_STORYLINE} 개)
	 */
	public static final int STORYLINE_OVERHEAD = 100;

	private CandidateCost() {
	}

	/** 후보 하나의 토큰 어림 */
	public static int candidate(int titleCodePoints, int summaryCodePoints) {
		if (titleCodePoints < 0 || summaryCodePoints < 0) {
			throw new IllegalArgumentException("코드 포인트 수가 음수다");
		}
		return tokens(titleCodePoints + summaryCodePoints + CANDIDATE_OVERHEAD);
	}

	/**
	 * 큰 스토리라인 묶음들의 토큰 어림
	 * @param titleCodePoints 묶음마다 이름의 코드 포인트 수
	 */
	public static int storylines(List<Integer> titleCodePoints) {
		int codePoints = 0;
		for (int title : titleCodePoints) {
			if (title < 0) {
				throw new IllegalArgumentException("코드 포인트 수가 음수다");
			}
			codePoints += title + STORYLINE_OVERHEAD;
		}
		return tokens(codePoints);
	}

	/**
	 * 후보가 쓸 수 있는 토큰 — 모델의 입력 토큰 예산에서 지시 구획, 사용자 프로필 구획(구독 주제 · 지역 선호 · 자유 텍스트 관심사 · 고정 틀),
	 * 큰 스토리라인 묶음의 몫을 뺀 것. 0 보다 작으면 0
	 * @param topicRows 구독 행 수
	 * @param excludedRegions 제외한 지역 수
	 * @param storylineTokens 묶음들의 토큰 어림({@link #storylines})
	 */
	public static int candidateBudget(int modelBudget, int interestTextCodePoints, int topicRows, int excludedRegions,
			int storylineTokens) {
		if (storylineTokens < 0) {
			throw new IllegalArgumentException("묶음 몫이 음수다");
		}
		int profile = tokens(
				interestTextCodePoints + (topicRows + excludedRegions) * PROFILE_TAG_COST + PROFILE_OVERHEAD);
		return Math.max(0, modelBudget - INSTRUCTION_RESERVE - profile - storylineTokens);
	}

	/** 코드 포인트 수의 토큰 어림 — 어림 비율의 정본. 프롬프트 구성의 시험이 이 함수로 견준다 */
	public static int tokens(int codePoints) {
		return (int) Math.ceil(codePoints * TOKENS_PER_CODE_POINT);
	}

}
