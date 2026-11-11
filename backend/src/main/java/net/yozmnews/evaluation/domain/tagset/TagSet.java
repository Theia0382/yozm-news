package net.yozmnews.evaluation.domain.tagset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.platform.ChangeActor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 태그 체계 버전 aggregate — 버전 하나가 태그 세트 하나다 — docs/specs/evaluation.md §4 · evaluation-R3 · evaluation-R4.
 * <p>
 * 버전 내용(질문 틀 · 질문 · 태그 · 중요도 기준)과 계보를 바꾸는 메서드가 없다 — 만들 때 한 번 쓴다. 활성 표시는 aggregate 를 다시
 * 저장하지 않고 {@link TagSetRepository#deactivate} 로 끈다 — 이미 있는 aggregate 를 저장하면 자식 행을 지우고 다시 넣는다. 태그
 * 기준 · 저장 기준은 이 aggregate 밖에서 바뀐다.
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

	@MappedCollection(idColumn = "tag_set_id")
	private final Set<LineageRow> lineage;

	private final ChangeActor createdBy;

	private final Instant createdAt;

	@PersistenceCreator
	TagSet(@Nullable Long id, int number, boolean active, String topicTemplate, String regionTemplate,
			String formatQuestion, String importanceQuestion, Set<TagRow> tags, Set<ImportanceRubricRow> rubrics,
			Set<LineageRow> lineage, ChangeActor createdBy, Instant createdAt) {
		this.id = id;
		this.number = number;
		this.active = active;
		this.topicTemplate = Objects.requireNonNull(topicTemplate, "topicTemplate");
		this.regionTemplate = Objects.requireNonNull(regionTemplate, "regionTemplate");
		this.formatQuestion = Objects.requireNonNull(formatQuestion, "formatQuestion");
		this.importanceQuestion = Objects.requireNonNull(importanceQuestion, "importanceQuestion");
		this.tags = Set.copyOf(tags);
		this.rubrics = Set.copyOf(rubrics);
		this.lineage = Set.copyOf(lineage);
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 첫 버전 — 번호 1 · 활성 · 계보 없음 — 을 만든다(evaluation-R2) */
	public static TagSet first(TagSetContent content, ChangeActor actor, Instant now) {
		return create(1, content, Set.of(), actor, now);
	}

	/**
	 * 기준 버전 다음 버전 — 번호는 기준 버전 + 1, 활성, 원문의 계보를 행으로 편다(evaluation-R16).
	 * <p>
	 * 기준 버전을 끄는 일은 부르는 쪽이 저장 전에 한다 — 활성 행은 하나다(evaluation-R3).
	 */
	public static TagSet next(TagSet base, TagSetDefinition definition, ChangeActor actor, Instant now) {
		Set<LineageRow> lineage = new HashSet<>();
		for (LineageDef entry : definition.lineage().stream().map(located -> located.value()).toList()) {
			for (TagKey from : entry.from()) {
				for (TagKey to : entry.to()) {
					lineage.add(new LineageRow(entry.kind(), from.value(), to.value(), entry.change()));
				}
			}
		}
		return create(base.number() + 1, definition.content(), lineage, actor, now);
	}

	private static TagSet create(int number, TagSetContent content, Set<LineageRow> lineage, ChangeActor actor,
			Instant now) {
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
		return new TagSet(null, number, true, content.topicTemplate().text(), content.regionTemplate().text(),
				content.formatQuestion(), content.importanceQuestion(), tags, rubrics, lineage, actor, now);
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

	public ChangeActor createdBy() {
		return this.createdBy;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	/**
	 * 이 버전의 계보 — 바로 앞 버전의 키에서 이어지는 항목. 행을 항목으로 되돌린다 — 이름 변경은 행 하나, 분할은 이전 키로, 병합은 새 키로
	 * 묶는다. 순서는 태그 종류 → 계보 종류 → 묶은 키 순이다.
	 */
	public List<LineageDef> lineage() {
		Map<String, List<LineageRow>> groups = new LinkedHashMap<>();
		this.lineage.stream()
			.sorted(Comparator.comparing(LineageRow::tagKind)
				.thenComparing(LineageRow::kind)
				.thenComparing(LineageRow::fromKey)
				.thenComparing(LineageRow::toKey))
			.forEach(row -> groups.computeIfAbsent(group(row), key -> new ArrayList<>()).add(row));
		List<LineageDef> lineage = new ArrayList<>();
		for (List<LineageRow> rows : groups.values()) {
			LineageRow first = rows.get(0);
			lineage.add(new LineageDef(first.tagKind(), first.kind(),
					rows.stream().map(LineageRow::fromKey).distinct().map(TagKey::new).toList(),
					rows.stream().map(LineageRow::toKey).distinct().map(TagKey::new).toList()));
		}
		return lineage;
	}

	/** 행이 속한 항목 — 한 태그 종류 안에서 이름 변경 · 분할은 이전 키가, 병합은 새 키가 항목을 가른다(evaluation-R22) */
	private static String group(LineageRow row) {
		String key = switch (row.kind()) {
			case RENAME, SPLIT -> row.fromKey();
			case MERGE -> row.toKey();
		};
		return row.tagKind() + " " + row.kind() + " " + key;
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
