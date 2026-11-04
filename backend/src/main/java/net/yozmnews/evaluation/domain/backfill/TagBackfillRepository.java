package net.yozmnews.evaluation.domain.backfill;

import java.util.List;

import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.threshold.StoreCoverage;

/**
 * 빠진 태그 채움 저장소 — docs/specs/evaluation.md §4.
 * <p>
 * 구현은 {@code adapter/out/persistence} 의 {@code JdbcTagBackfillRepository} 다. 끝난 채움을 지우는 연산은 없다 — 저장 바닥 계산의
 * 입력이다.
 */
public interface TagBackfillRepository {

	/** 그 버전 · 그 태그 종류의 덮음을 읽는다 — 끝났고 그 버전에서 건너뛴 평가가 없는 채움만, 요청 순 */
	List<StoreCoverage> coverages(TagSetId version, MultiTagKind kind);

}
