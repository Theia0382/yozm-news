package net.yozmnews.evaluation.domain.tagset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.TagSetId;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 태그 체계 버전 aggregate — 버전 하나가 태그 세트 하나다 — docs/specs/evaluation.md §4 · evaluation-R3 · evaluation-R4.
 * <p>
 * 버전 내용(질문 틀 · 질문 · 태그 · 중요도 기준)을 바꾸는 메서드가 없다 — 만들 때 한 번 쓴다. 두 기준은 이 aggregate 밖에서 바뀐다.
 */
@Table("tag_sets")
public final class TagSet {

	@Id
	private final @Nullable Long id;

	private final int number;

	private final boolean active;

	private final String topicTemplate;

	private final String regionTemplate;

	private final String formatQuestion;

	private final String importanceQuestion;

	@MappedCollection(idColumn = "tag_set_id")
	private final Set<TagRow> tags;

	@MappedCollection(idColumn = "tag_set_id")
	private final Set<ImportanceRubricRow> rubrics;

	private final ChangeActor createdBy;

	private final Instant createdAt;

	@PersistenceCreator
	TagSet(@Nullable Long id, int number, boolean active, String topicTemplate, String regionTemplate,
			String formatQuestion, String importanceQuestion, Set<TagRow> tags, Set<ImportanceRubricRow> rubrics,
			ChangeActor createdBy, Instant createdAt) {
		this.id = id;
		this.number = number;
		this.active = active;
		this.topicTemplate = Objects.requireNonNull(topicTemplate, "topicTemplate");
		this.regionTemplate = Objects.requireNonNull(regionTemplate, "regionTemplate");
		this.formatQuestion = Objects.requireNonNull(formatQuestion, "formatQuestion");
		this.importanceQuestion = Objects.requireNonNull(importanceQuestion, "importanceQuestion");
		this.tags = Set.copyOf(tags);
		this.rubrics = Set.copyOf(rubrics);
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 첫 버전 — 번호 1 · 활성 — 을 만든다(evaluation-R2) */
	public static TagSet first(TagSetContent content, ChangeActor actor, Instant now) {
		Set<TagRow> tags = new HashSet<>();
		int order = 0;
		for (TopicCategoryDef topic : content.topics()) {
			tags.add(new TagRow(TagKind.TOPIC, topic.key().value(), null, topic.name(), topic.description(), false,
					order++));
			for (TagDef subtopic : topic.subtopics()) {
				tags.add(new TagRow(TagKind.TOPIC, subtopic.key().value(), topic.key().value(), subtopic.name(),
						subtopic.description(), false, order++));
			}
		}
		for (TagDef region : content.regions()) {
			tags.add(new TagRow(TagKind.REGION, region.key().value(), null, region.name(), region.description(), false,
					order++));
		}
		for (FormatDef format : content.formats()) {
			tags.add(new TagRow(TagKind.FORMAT, format.key().value(), null, format.name(), format.description(),
					format.excludedByDefault(), order++));
		}
		Set<ImportanceRubricRow> rubrics = new HashSet<>();
		for (ImportanceLevelDef level : content.importance()) {
			rubrics.add(new ImportanceRubricRow(level.level(), level.description()));
		}
		return new TagSet(null, 1, true, content.topicTemplate().text(), content.regionTemplate().text(),
				content.formatQuestion(), content.importanceQuestion(), tags, rubrics, actor, now);
	}

	/**
	 * 버전 식별자를 돌려준다.
	 * @throws IllegalStateException 아직 저장하지 않았을 때
	 */
	public TagSetId id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 태그 체계 버전이다");
		}
		return new TagSetId(this.id);
	}

	public int number() {
		return this.number;
	}

	public boolean active() {
		return this.active;
	}

	/** 행을 버전 내용으로 옮긴다 — 순서는 {@code sort_order}, 중요도는 수준 순 */
	public TagSetContent content() {
		List<TagRow> rows = this.tags.stream().sorted(Comparator.comparingInt(TagRow::sortOrder)).toList();
		List<TopicCategoryDef> topics = new ArrayList<>();
		List<TagDef> regions = new ArrayList<>();
		List<FormatDef> formats = new ArrayList<>();
		for (TagRow row : rows) {
			switch (row.kind()) {
				case TOPIC -> {
					if (row.parentKey() == null) {
						List<TagDef> subtopics = rows.stream()
							.filter(child -> child.kind() == TagKind.TOPIC && row.key().equals(child.parentKey()))
							.map(child -> new TagDef(new TagKey(child.key()), child.name(), child.description()))
							.toList();
						topics.add(new TopicCategoryDef(new TagKey(row.key()), row.name(), row.description(), subtopics));
					}
				}
				case REGION -> regions.add(new TagDef(new TagKey(row.key()), row.name(), row.description()));
				case FORMAT -> formats.add(new FormatDef(new TagKey(row.key()), row.name(), row.description(),
						row.excludedByDefault()));
			}
		}
		List<ImportanceLevelDef> importance = this.rubrics.stream()
			.sorted(Comparator.comparingInt(ImportanceRubricRow::level))
			.map(rubric -> new ImportanceLevelDef(rubric.level(), rubric.description()))
			.toList();
		return new TagSetContent(new QuestionTemplate(this.topicTemplate), new QuestionTemplate(this.regionTemplate),
				this.formatQuestion, this.importanceQuestion, topics, regions, formats, importance);
	}

}
