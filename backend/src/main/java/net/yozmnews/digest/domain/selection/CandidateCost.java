package net.yozmnews.digest.domain.selection;

import java.util.List;
import java.util.Objects;

/**
 * 입력 토큰의 추정 — 정본. 프롬프트 구성은 이 추정을 따른다 — docs/specs/digest.md §5
 * <p>
 * 보내기 전에는 공급자가 세는 토큰 수를 알 수 없어 코드 포인트로 어림한다. 한국어 기사 문장은 공급자 토크나이저에서 대략 코드 포인트 하나에 토큰
 * 1 개 이하라 {@link #TOKENS_PER_CODE_POINT} 를 1.0 으로 두어 넉넉히 센다. 후보는 사건이고, 사건 하나의 어림은 표본 기사 줄마다의 어림과
 * 사건 몫의 합이다. 상수는 모두 측정 전 목표다 — 다이제스트에 기록되는 실제 입력 토큰(digest-R1)으로 고친다.
 */
public final class CandidateCost {

	/** 코드 포인트 하나의 토큰 어림 */
	public static final double TOKENS_PER_CODE_POINT = 1.0;

	/** 기사 줄 하나에서 제목 · 요약문 밖의 몫(코드 포인트) — 출처 · 발행 시각 · 주제 · 지역 태그 · 구분 기호 */
	public static final int CANDIDATE_OVERHEAD = 80;

	/**
	 * 사건 하나에서 기사 줄 밖의 몫(코드 포인트) — 사건 괄호와, 사건이 든 항목의 괄호 · 항목 별칭 · 칸 이름 · 구분 기호. 항목의 몫을 항목
	 * 안 사건마다 센다. 출력 스키마의 몫은 세지 않는다 — 헤드라인 칸은 배열이라 항목 수와 함께 늘지 않고, 주요 뉴스 칸이 가득 찬 몫은
	 * {@link #INSTRUCTION_RESERVE} 가 덮는다
	 */
	public static final int EVENT_OVERHEAD = 40;

	/**
	 * 지시 구획(역할 · 규칙 · 주요 뉴스 칸이 가득 찬 출력 스키마 · 다시 묻는 진단)의 몫(토큰) — 모델 예산에서 먼저 뺀다. 스키마는 구조화 출력이어도
	 * 공급자가 입력으로 셀 수 있어 늘 센다. 주요 뉴스 칸은 항목 별칭을 키로 한 객체라 주요 뉴스 항목의 위쪽까지 덮는다
	 */
	public static final int INSTRUCTION_RESERVE = 6_000;

	/** 사용자 프로필 구획에서 구독 주제 · 지역 선호 하나의 몫(코드 포인트) — 이름과 구분 기호 */
	public static final int PROFILE_TAG_COST = 20;

	/**
	 * 사용자 구획의 고정 틀(코드 포인트) — 문서 · 구획 키 · 괄호 · 시간대 ID. 가장 긴 IANA 시간대 ID 를 덮는다 — 구독 주제 · 지역 선호 ·
	 * 항목 밖의 글이다
	 */
	public static final int PROFILE_OVERHEAD = 200;

	private CandidateCost() {
	}

	/** 기사 줄 하나의 토큰 어림 */
	public static int candidate(int titleCodePoints, int summaryCodePoints) {
		if (titleCodePoints < 0 || summaryCodePoints < 0) {
			throw new IllegalArgumentException("코드 포인트 수가 음수다");
		}
		return tokens(titleCodePoints + summaryCodePoints + CANDIDATE_OVERHEAD);
	}

	/**
	 * 사건 하나의 토큰 어림 — 기사 줄 어림의 합에 {@link #EVENT_OVERHEAD} 를 더한다
	 * @param articleTokens 내용을 읽은 표본 기사마다 기사 줄 어림({@link #candidate})
	 * @throws IllegalArgumentException 기사 줄이 없거나 음수가 있을 때 — 기사 줄이 없는 사건은 어림하지 않고 뺀다({@link BudgetCut})
	 */
	public static int event(List<Integer> articleTokens) {
		Objects.requireNonNull(articleTokens, "articleTokens");
		if (articleTokens.isEmpty()) {
			throw new IllegalArgumentException("기사 줄이 없는 사건이다");
		}
		int sum = 0;
		for (int tokens : articleTokens) {
			if (tokens < 0) {
				throw new IllegalArgumentException("기사 줄 어림이 음수다");
			}
			sum += tokens;
		}
		return sum + tokens(EVENT_OVERHEAD);
	}

	/**
	 * 후보가 쓸 수 있는 토큰 — 모델의 입력 토큰 예산에서 지시 구획과 사용자 프로필 구획(구독 주제 · 지역 선호 · 고정 틀)의
	 * 몫을 뺀 것. 0 보다 작으면 0
	 * @param topicRows 구독 행 수
	 * @param excludedRegions 제외한 지역 수
	 */
	public static int candidateBudget(int modelBudget, int topicRows, int excludedRegions) {
		int profile = tokens((topicRows + excludedRegions) * PROFILE_TAG_COST + PROFILE_OVERHEAD);
		return Math.max(0, modelBudget - INSTRUCTION_RESERVE - profile);
	}

	/** 코드 포인트 수의 토큰 어림 — 어림 비율의 정본. 프롬프트 구성의 시험이 이 함수로 견준다 */
	public static int tokens(int codePoints) {
		return (int) Math.ceil(codePoints * TOKENS_PER_CODE_POINT);
	}

}
