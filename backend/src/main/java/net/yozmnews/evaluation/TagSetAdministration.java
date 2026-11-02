package net.yozmnews.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 태그 체계의 운영 — 태그 기준 · 저장 기준과 버전. admin 이 운영자 요청으로 부른다 — docs/specs/evaluation.md §3.
 * <p>
 * 구현은 {@code application} 의 {@code TagSetAdministrationService} 다.
 */
public interface TagSetAdministration {

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

	/** 버전 목록을 번호가 큰 것부터 돌려준다 */
	List<TagSetVersionView> versions();

	/**
	 * 버전의 원문을 돌려준다 — 그 버전의 내용과 지금 태그 기준 · 저장 기준으로 만든다(evaluation-R27).
	 * @return 없는 버전이면 빈 값
	 */
	Optional<String> versionText(TagSetId id);

}
