package net.yozmnews.tag.application;

import java.util.Objects;

import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagSetNames;
import net.yozmnews.tag.TagSetThresholds;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.tag.domain.tagset.TagSet;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.springframework.stereotype.Service;

/**
 * 공개 API {@link TagSetDirectory} 의 구현 — docs/specs/tag.md §4 · tag-R3 · tag-R9.
 * <p>
 * 활성 버전 · 버전 하나를 읽어 공개 타입으로 옮긴다 — 옮기는 일은 {@link TagSetViews} 가 한다. 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
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

	@Override
	public TagSetView tagSet(TagSetId id) {
		Objects.requireNonNull(id, "id");
		return TagSetViews.view(this.tagSets.findById(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 태그 체계 버전: " + id.value())));
	}

	@Override
	public TagSetNames names(TagSetId id) {
		TagSetView view = tagSet(id);
		return new TagSetNames(view.id(),
				view.topics().stream().map(category -> new TagName(category.key(), category.name())).toList(),
				view.topics()
					.stream()
					.flatMap(category -> category.subtopics().stream())
					.map(subtopic -> new TagName(subtopic.key(), subtopic.name()))
					.toList(),
				view.regions().stream().map(region -> new TagName(region.key(), region.name())).toList());
	}

	/**
	 * 활성 버전을 읽는다.
	 * @throws IllegalStateException 활성 버전이 없을 때 — tag-R3
	 */
	static TagSet active(TagSetRepository tagSets) {
		return tagSets.findByActiveTrue()
			.orElseThrow(() -> new IllegalStateException("활성 태그 체계 버전이 없다 — 첫 기동 적재의 불변식이 깨졌다"));
	}

}
