package net.yozmnews.evaluation.domain.tagset;

import java.math.BigDecimal;
import java.util.List;

import net.yozmnews.platform.StrictYamlBinding.Located;
import net.yozmnews.platform.TextLocation;
import net.yozmnews.platform.YamlPath;
import org.jspecify.annotations.Nullable;

/**
 * 형(type) 검사를 마친 태그 체계 초안 — 모든 항목이 선택이고, 값마다 경로와 위치가 붙어 있다. 의미 검사는 {@link TagSetValidator}
 * 가 한다.
 */
record TagSetDraft(TextLocation location, @Nullable QuestionsDraft questions, @Nullable ThresholdsDraft thresholds,
		@Nullable Located<List<TopicDraft>> topics, @Nullable Located<List<TagDraft>> regions,
		@Nullable Located<List<FormatDraft>> formats, @Nullable Located<List<LevelDraft>> importance,
		@Nullable Located<List<LineageDraft>> lineage) {

	/** 질문 문구 초안 */
	record QuestionsDraft(YamlPath path, TextLocation location, @Nullable Located<String> topic,
			@Nullable Located<String> region, @Nullable Located<String> format, @Nullable Located<String> importance) {

	}

	/** 처음 태그 기준 · 저장 기준 초안 */
	record ThresholdsDraft(YamlPath path, TextLocation location, @Nullable ThresholdDraft topic,
			@Nullable ThresholdDraft region) {

	}

	/** 한 태그 종류의 처음 태그 기준 · 저장 기준 초안 */
	record ThresholdDraft(YamlPath path, TextLocation location, @Nullable Located<BigDecimal> tagThreshold,
			@Nullable Located<BigDecimal> storeThreshold) {

	}

	/** 주제 대분류 초안 */
	record TopicDraft(YamlPath path, TextLocation location, @Nullable Located<String> key,
			@Nullable Located<String> name, @Nullable Located<String> description,
			@Nullable Located<List<TagDraft>> subtopics) {

	}

	/** 주제 중분류 · 지역 초안 */
	record TagDraft(YamlPath path, TextLocation location, @Nullable Located<String> key, @Nullable Located<String> name,
			@Nullable Located<String> description) {

	}

	/** 형식 초안 */
	record FormatDraft(YamlPath path, TextLocation location, @Nullable Located<String> key,
			@Nullable Located<String> name, @Nullable Located<String> description,
			@Nullable Located<Boolean> excludedByDefault) {

	}

	/** 중요도 수준 초안 */
	record LevelDraft(YamlPath path, TextLocation location, @Nullable Located<Integer> level,
			@Nullable Located<String> description) {

	}

	/** 계보 항목 초안 — evaluation-R22 */
	record LineageDraft(YamlPath path, TextLocation location, @Nullable Located<String> kind,
			@Nullable Located<String> change, @Nullable Located<List<Located<String>>> from,
			@Nullable Located<List<Located<String>>> to) {

	}

}
