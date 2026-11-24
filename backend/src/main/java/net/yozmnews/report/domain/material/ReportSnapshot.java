package net.yozmnews.report.domain.material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import net.yozmnews.report.domain.input.CoverageSection;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.FlowInterestLine;
import net.yozmnews.report.domain.input.FlowMaterial;
import net.yozmnews.report.domain.input.InputSection;
import net.yozmnews.report.domain.input.InterestsSection;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.input.TopicCoverageLine;
import net.yozmnews.report.domain.input.TopicInterestLine;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TagSetId;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 통계 스냅숏 꼴의 정본 — 쓰기와 읽기 · docs/specs/report.md §5 · report-R19
 * <p>
 * 생성에 쓴 통계를 JSON 객체 하나로 쓴다 — 꼴의 판, 키의 태그 체계 버전, 기간, 입력에 든 갈래의 칸만. 보도량은 기간 시각의 종류를 함께 싣고
 * (구독 주제 {@value #FIRST_EVALUATED} · 흐름 {@value #PUBLISHED}), 이은 직전 값과 판정을 함께 남긴다. 주제 이름은 싣고 사건 줄 · 정리의
 * 글은 싣지 않는다. 읽기는 판이 {@link #VERSION} 과 같을 때만 다음 리포트가 견줄 기사 수를 낸다.
 */
public final class ReportSnapshot {

	/** 꼴의 판 — 올리면 옛 판을 읽는 길이나 옮기는 마이그레이션을 함께 둔다 */
	public static final int VERSION = 1;

	/** 구독 주제 보도량의 기간 시각 — 첫 평가 시각 */
	public static final String FIRST_EVALUATED = "FIRST_EVALUATED";

	/** 흐름 보도량의 기간 시각 — 사건 배정의 발행 시각 */
	public static final String PUBLISHED = "PUBLISHED";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ReportSnapshot() {
	}

	/**
	 * 직전 리포트의 스냅숏에서 다음 리포트가 견줄 값
	 *
	 * @param tagSet 주제 키의 태그 체계 버전
	 * @param topics 구독 행 키 → 기사 수 — 보도량 갈래가 없었으면 빈 맵
	 * @param flows 흐름 — 추적한 흐름 갈래가 없었으면 빈 목록
	 */
	public record SnapshotValues(TagSetId tagSet, Map<String, Long> topics, List<SnapshotFlow> flows) {

		public SnapshotValues {
			Objects.requireNonNull(tagSet, "tagSet");
			topics = Map.copyOf(topics);
			flows = List.copyOf(flows);
		}

	}

	/** 스냅숏의 흐름 하나 — 헤드 · 꼭대기들 · 기사 수 */
	public record SnapshotFlow(StorylineId head, Set<StorylineId> tops, long articles) {

		public SnapshotFlow {
			Objects.requireNonNull(head, "head");
			tops = Set.copyOf(tops);
		}

	}

	/** 생성에 쓴 통계를 쓴다 */
	public static String write(ReportPeriod period, TagSetId tagSet, List<InputSection> sections) {
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(tagSet, "tagSet");
		ObjectNode root = JSON.createObjectNode();
		root.put("version", VERSION);
		root.put("tagSet", tagSet.value());
		ObjectNode at = root.putObject("period");
		at.put("kind", period.kind().name());
		at.put("start", period.start().toString());
		at.put("end", period.end().toString());
		for (InputSection section : sections) {
			switch (section) {
				case StorylinesSection storylines -> writeFlows(root.putObject("storylines"), storylines);
				case CoverageSection coverage -> writeCoverage(root.putObject("coverage"), coverage);
				case InterestsSection interests -> writeInterests(root.putObject("interests"), interests);
			}
		}
		return JSON.writeValueAsString(root);
	}

	/**
	 * 다음 리포트가 견줄 값을 읽는다.
	 * @return 모르는 판이거나 읽지 못하면 빈 값
	 */
	public static Optional<SnapshotValues> read(@Nullable String json) {
		if (json == null) {
			return Optional.empty();
		}
		try {
			JsonNode root = JSON.readTree(json);
			if (root == null || !root.isObject() || !root.path("version").isInt()
					|| root.path("version").intValue() != VERSION || !root.path("tagSet").canConvertToLong()) {
				return Optional.empty();
			}
			Map<String, Long> topics = new LinkedHashMap<>();
			for (JsonNode topic : root.path("coverage").path("topics")) {
				topics.put(text(topic, "key"), number(topic, "articles"));
			}
			List<SnapshotFlow> flows = new ArrayList<>();
			for (JsonNode flow : root.path("storylines").path("flows")) {
				Set<StorylineId> tops = new TreeSet<>(Comparator.comparingLong(StorylineId::value));
				for (JsonNode top : flow.path("tops")) {
					tops.add(new StorylineId(top.longValue()));
				}
				flows.add(new SnapshotFlow(new StorylineId(number(flow, "head")), tops, number(flow, "articles")));
			}
			return Optional.of(new SnapshotValues(new TagSetId(root.path("tagSet").longValue()), topics, flows));
		}
		catch (JacksonException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private static void writeCoverage(ObjectNode node, CoverageSection coverage) {
		node.put("basis", FIRST_EVALUATED);
		ArrayNode topics = node.putArray("topics");
		for (TopicCoverageLine line : coverage.topics()) {
			ObjectNode topic = topics.addObject();
			topic.put("key", line.key());
			topic.put("name", line.name());
			count(topic, line.articles());
		}
	}

	private static void writeFlows(ObjectNode node, StorylinesSection storylines) {
		node.put("basis", PUBLISHED);
		ArrayNode flows = node.putArray("flows");
		for (FlowMaterial material : storylines.flows()) {
			ObjectNode flow = flows.addObject();
			flow.put("head", material.head().value());
			ArrayNode tops = flow.putArray("tops");
			material.tops().stream().mapToLong(StorylineId::value).sorted().forEach(tops::add);
			count(flow, material.articles());
			flow.put("trackedInPeriod", material.trackedInPeriod());
		}
	}

	private static void writeInterests(ObjectNode node, InterestsSection interests) {
		ArrayNode topics = node.putArray("topics");
		for (TopicInterestLine line : interests.topics()) {
			ObjectNode topic = topics.addObject();
			topic.put("key", line.key());
			topic.put("name", line.name());
			level(topic, "start", line.start());
			level(topic, "end", line.end());
			topic.put("shift", line.shift().name());
		}
		ArrayNode flows = node.putArray("flows");
		for (FlowInterestLine line : interests.flows()) {
			ObjectNode flow = flows.addObject();
			flow.put("head", line.head().value());
			level(flow, "start", line.start());
			level(flow, "end", line.end());
			flow.put("shift", line.shift().name());
		}
	}

	private static void count(ObjectNode node, Count count) {
		node.put("articles", count.current());
		if (count.previous() == null) {
			node.putNull("previous");
		}
		else {
			node.put("previous", count.previous().longValue());
		}
		node.put("trend", count.trend().name());
	}

	private static void level(ObjectNode node, String field, @Nullable Double value) {
		if (value == null) {
			node.putNull(field);
		}
		else {
			node.put(field, value.doubleValue());
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.isString()) {
			throw new IllegalArgumentException("스냅숏의 " + field + " 가 문자열이 아니다");
		}
		return value.stringValue();
	}

	private static long number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (!value.canConvertToLong() || !value.isIntegralNumber()) {
			throw new IllegalArgumentException("스냅숏의 " + field + " 가 정수가 아니다");
		}
		return value.longValue();
	}

}
