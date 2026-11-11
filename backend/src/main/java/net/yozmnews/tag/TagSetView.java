package net.yozmnews.tag;

import java.util.List;
import java.util.Objects;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;

/**
 * 활성 태그 체계의 내용 — docs/specs/tag.md §3.
 * <p>
 * 목록은 파일에 적힌 순서다. 내용은 만든 뒤 바뀌지 않는다(tag-R4).
 *
 * @param number 버전 번호
 */
public record TagSetView(TagSetId id, int number, List<TopicCategory> topics, List<AskedTag> regions,
		FormatChoice format, ImportanceScale importance) {

	public TagSetView {
		Objects.requireNonNull(id, "id");
		topics = List.copyOf(topics);
		regions = List.copyOf(regions);
		Objects.requireNonNull(format, "format");
		Objects.requireNonNull(importance, "importance");
	}

	/** 이 버전의 질문 묶음 — 태그 판정 · 다시 읽기에 넘긴다(tag-R14) */
	public TagQuestions questions() {
		return new TagQuestions(this.topics, this.regions, this.format, this.importance);
	}

}
