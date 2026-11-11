package net.yozmnews.tag.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.ThresholdChangeView;
import net.yozmnews.tag.domain.backfill.TagBackfillRepository;
import net.yozmnews.tag.domain.threshold.StoreFloors;
import org.springframework.stereotype.Component;

/**
 * 저장 바닥 결선 — 그 버전 · 태그 종류의 이력과 끝난 채움의 덮음으로 {@link StoreFloors} 를 만든다 · docs/specs/tag.md §4 ·
 * tag-R32. 트랜잭션을 열지 않는다 — 부르는 쪽의 트랜잭션(한 스냅숏) 안에서 덮음을 읽는다. 역할 애너테이션이 없다(두 역할 공통).
 */
@Component
class StoreFloorsReader {

	private final TagBackfillRepository backfills;

	StoreFloorsReader(TagBackfillRepository backfills) {
		this.backfills = backfills;
	}

	/**
	 * @param changes 그 버전의 태그 기준 · 저장 기준 이력 — 부르는 쪽이 같은 트랜잭션에서 읽은 것(저장소 순서 그대로)
	 */
	StoreFloors floors(TagSetId version, MultiTagKind kind, List<ThresholdChangeView> changes) {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(changes, "changes");
		return StoreFloors.of(kind, changes, this.backfills.coverages(version, kind));
	}

}
