package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.ChangeActor;

/**
 * 태그 체계의 운영 — 태그 기준 · 저장 기준과 버전. admin 이 운영자 요청으로 부른다 — docs/specs/evaluation.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code TagSetAdministrationService} 다.
 */
public interface TagSetAdministration {

	/** 재평가 기간의 상한(일) — 정본이다(evaluation-R25) */
	int MAX_REEVALUATION_DAYS = 30;

	/**
	 * 활성 버전의 한 태그 종류의 태그 기준 · 저장 기준을 바꾼다 — 한 트랜잭션이고 같은 태그 종류의 변경은 줄을 선다(evaluation-R11 · R12).
	 * @param actor 운영자 요청은 {@link ChangeActor#OPERATOR}
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	ThresholdChangeResult changeThresholds(MultiTagKind kind, BigDecimal tagThreshold, BigDecimal storeThreshold,
			ChangeActor actor);

	/**
	 * 활성 버전의 태그 기준 · 저장 기준 이력을 최근 것부터 돌려준다(evaluation-R13).
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	List<ThresholdChangeView> thresholdChanges();

	/**
	 * 활성 버전의 기준 상태 — 태그 종류마다 지금 값 · 지난 저장 기준 최댓값 · 어긋난 상태와 이력. 읽기 전용 {@code REPEATABLE READ}
	 * 트랜잭션 하나(한 스냅숏)에서 읽는다(evaluation-R36).
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	ThresholdStatusView thresholdStatus();

	/**
	 * 기준 미리보기 — 활성 버전 · 한 태그 종류의 제안 값으로 기간 안 평가의 판정이 어떻게 달라지는지. 아무것도 쓰지 않고, 읽기 전용
	 * {@code REPEATABLE READ} 트랜잭션 하나에서 읽는다. 범위 · 같은 값 판정과 경고는 {@link #changeThresholds} 와 같다(evaluation-R35).
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	ThresholdPreviewResult previewThresholds(MultiTagKind kind, BigDecimal tagThreshold, BigDecimal storeThreshold,
			EvaluationPeriod period);

	/** 버전 목록을 번호가 큰 것부터 돌려준다 */
	List<TagSetVersionView> versions();

	/**
	 * 버전의 원문을 돌려준다 — 그 버전의 내용과 지금 태그 기준 · 저장 기준으로 만든다(evaluation-R27).
	 * @return 없는 버전이면 빈 값
	 */
	Optional<String> versionText(TagSetId id);

	/**
	 * 원문을 확인한다 — 검증하고 그 순간의 활성 버전과 대조해 변경 목록과 재평가 기간에 처음 본 기사 수를 돌려준다. 아무것도 쓰지 않는다
	 * (evaluation-R16 · R22 ~ R25).
	 * @param recentDays 재평가 기간(일) — 0 ~ {@link #MAX_REEVALUATION_DAYS}
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	TagSetReviewResult review(String text, int recentDays);

	/**
	 * 원문으로 새 버전을 만들어 활성으로 바꾼다 — 재평가 대상 기사를 트랜잭션 밖에서 읽은 뒤, 한 트랜잭션에서 표를 잠그고 그 순간의 활성
	 * 버전과 다시 대조해 쓴다(evaluation-R16 · R25).
	 * @param recentDays 재평가 기간(일) — 0 ~ {@link #MAX_REEVALUATION_DAYS}. 0 이면 재평가하지 않는다
	 * @param actor 새 버전과 그 처음 태그 기준 · 저장 기준 이력 행의 행위자 — 운영자 요청은 {@link ChangeActor#OPERATOR}
	 * @throws IllegalStateException 활성 버전이 없을 때
	 */
	TagSetActivationResult activate(String text, int recentDays, ChangeActor actor);

}
