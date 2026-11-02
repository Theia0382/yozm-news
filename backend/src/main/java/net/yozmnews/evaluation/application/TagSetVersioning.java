package net.yozmnews.evaluation.application;

import java.util.List;
import java.util.Optional;

import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetWriter;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Component;

/**
 * 태그 체계 버전 연산 — 버전 목록 · 원문 내보내기 — docs/specs/evaluation.md §4 · evaluation-R27.
 * <p>
 * {@link TagSetAdministrationService} 가 맡긴다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Component
class TagSetVersioning {

	private final TagSetRepository tagSets;

	private final ThresholdRepository thresholds;

	TagSetVersioning(TagSetRepository tagSets, ThresholdRepository thresholds) {
		this.tagSets = tagSets;
		this.thresholds = thresholds;
	}

	/** 버전 목록 — 번호가 큰 것부터 */
	List<TagSetVersionView> versions() {
		return this.tagSets.findAllByOrderByNumberDesc().stream().map(TagSetVersioning::view).toList();
	}

	/** 버전의 원문 — 그 버전의 내용과 지금 태그 기준 · 저장 기준. 없는 버전이면 빈 값 */
	Optional<String> versionText(TagSetId id) {
		return this.tagSets.findById(id.value())
			.map(tagSet -> TagSetWriter.write(tagSet.content(), this.thresholds.current(tagSet.id())));
	}

	static TagSetVersionView view(TagSet tagSet) {
		return new TagSetVersionView(tagSet.id(), tagSet.number(), tagSet.active(), tagSet.createdBy(),
				tagSet.createdAt());
	}

}
