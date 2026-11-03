package net.yozmnews.evaluation.domain.threshold;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.ThresholdChangeView;

/**
 * 태그 기준 · 저장 기준 저장소 — 지금 행과 추가 전용 이력 — docs/specs/evaluation.md §4 · evaluation-R11 · evaluation-R13.
 * <p>
 * 구현은 {@code adapter/out/persistence} 의 {@code JdbcThresholdRepository} 다. 이력을 지우는 연산은 없다.
 */
public interface ThresholdRepository {

	/** 처음 태그 기준 · 저장 기준과 그 이력 첫 행(이전 값 없음)을 쓴다 */
	void insertInitial(TagSetId version, MultiTagKind kind, ThresholdValues values, ChangeActor actor, Instant at);

	/**
	 * 지금 태그 기준 · 저장 기준을 행 잠금으로 읽는다 — 트랜잭션 안에서 부른다.
	 * @return 행이 없으면 빈 값
	 */
	Optional<ThresholdValues> lockCurrent(TagSetId version, MultiTagKind kind);

	/**
	 * 이력 전체의 저장 기준 최댓값을 돌려준다.
	 * @throws IllegalStateException 이력이 없을 때 — 처음 값이 첫 행이라는 불변식이 깨진 것이다
	 */
	BigDecimal maxStoreThreshold(TagSetId version, MultiTagKind kind);

	/** 지금 행을 새 값으로 바꾸고 이력 행을 더한다 */
	void apply(TagSetId version, MultiTagKind kind, ThresholdValues previous, ThresholdValues next, ChangeActor actor,
			Instant at);

	/** 이력을 최근 것부터 돌려준다 */
	List<ThresholdChangeView> changes(TagSetId version);

	/**
	 * 지금 태그 기준 · 저장 기준을 돌려준다.
	 * @throws IllegalStateException 기준이 있는 태그 종류의 행이 하나라도 없을 때
	 */
	TagSetThresholds current(TagSetId version);

}
