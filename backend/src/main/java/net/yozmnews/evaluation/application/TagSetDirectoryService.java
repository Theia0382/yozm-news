package net.yozmnews.evaluation.application;

import net.yozmnews.evaluation.TagSetDirectory;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 API {@link TagSetDirectory} 의 구현 — docs/specs/evaluation.md §4 · evaluation-R3 · evaluation-R9.
 * <p>
 * 활성 버전을 읽어 공개 타입으로 옮긴다 — 옮기는 일은 {@link TagSetViews} 가 한다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class TagSetDirectoryService implements TagSetDirectory {

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	TagSetDirectoryService(TagSetRepository tagSets, ThresholdRepository thresholds) {
		this.tagSets = tagSets;
		this.thresholds = thresholds;
	}

	@Override
	public TagSetView activeTagSet() {
		return TagSetViews.view(active(this.tagSets));
	}

	@Override
	public TagSetThresholds activeThresholds() {
		return this.thresholds.current(active(this.tagSets).id());
	}

	/**
	 * 활성 버전을 읽는다.
	 * @throws IllegalStateException 활성 버전이 없을 때 — evaluation-R3
	 */
	static TagSet active(TagSetRepository tagSets) {
		return tagSets.findByActiveTrue()
			.orElseThrow(() -> new IllegalStateException("활성 태그 체계 버전이 없다 — 첫 기동 적재의 불변식이 깨졌다"));
	}

}
