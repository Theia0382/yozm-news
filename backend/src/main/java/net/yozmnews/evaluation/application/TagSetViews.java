package net.yozmnews.evaluation.application;

import java.util.List;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.tagset.QuestionTemplate;
import net.yozmnews.evaluation.domain.tagset.TagDef;
import net.yozmnews.evaluation.domain.tagset.TagSet;
import net.yozmnews.evaluation.domain.tagset.TagSetContent;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import org.springframework.stereotype.Component;

/**
 * 태그 체계 버전을 공개 타입으로 옮긴다 — 활성 버전 조회와 평가 결과의 버전 조회가 함께 쓰는 정본 · docs/specs/evaluation.md §4
 */
@Component
class TagSetViews {

	private final TagSetRepository tagSets;

	TagSetViews(TagSetRepository tagSets) {
		this.tagSets = tagSets;
	}

	/**
	 * 버전 하나를 읽는다.
	 * @throws IllegalStateException 그 버전이 없을 때 — 평가 행이 가리키는 버전은 지우지 않는다
	 */
	TagSetView of(TagSetId id) {
		return view(this.tagSets.findById(id.value())
			.orElseThrow(() -> new IllegalStateException("태그 체계 버전이 없다: " + id.value())));
	}

	/** 버전의 내용 — 질문 문장을 만들어 공개 타입으로 옮긴다(evaluation-R9) */
	static TagSetView view(TagSet tagSet) {
		TagSetContent content = tagSet.content();
		List<TopicCategory> topics = content.topics()
			.stream()
			.map(topic -> new TopicCategory(topic.key().value(), topic.name(), topic.description(),
					asked(topic.subtopics(), content.topicTemplate())))
			.toList();
		List<FormatOption> formats = content.formats()
			.stream()
			.map(format -> new FormatOption(format.key().value(), format.name(), format.description(),
					format.excludedByDefault()))
			.toList();
		List<ImportanceLevel> levels = content.importance()
			.stream()
			.map(level -> new ImportanceLevel(level.level(), level.description()))
			.toList();
		return new TagSetView(tagSet.id(), tagSet.number(), topics, asked(content.regions(), content.regionTemplate()),
				new FormatChoice(content.formatQuestion(), formats), new ImportanceScale(content.importanceQuestion(), levels));
	}

	private static List<AskedTag> asked(List<TagDef> tags, QuestionTemplate template) {
		return tags.stream()
			.map(tag -> new AskedTag(tag.key().value(), tag.name(), tag.description(), template.render(tag.description())))
			.toList();
	}

}
