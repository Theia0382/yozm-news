package net.yozmnews.tag.domain.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.ThresholdChangeView;

/**
 * 태그 기준 · 저장 기준 저장소 — 지금 행과 추가 전용 이력 — docs/specs/tag.md §4 · tag-R11 · tag-R13.
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
