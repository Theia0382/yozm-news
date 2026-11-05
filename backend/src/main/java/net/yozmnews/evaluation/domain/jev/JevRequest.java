package net.yozmnews.evaluation.domain.jev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jev 요청 본문 — docs/specs/evaluation.md §4 ArticleEvaluator · evaluation-R14 · evaluation-R37.
 * <p>
 * 평가의 질문은 넘긴 체계에서 읽는다. 질문 이름은 태그 종류 접두에 태그 키를 붙인 것이고, 응답은 같은 이름으로 답을 돌려준다. 형식 선택지는
 * 체계 순서, 중요도 수준 설명은 수준 순이다 — 응답의 수준 위치는 이 순서로 되찾는다({@link JevResponse}). 사건 소속 판정의 질문은 후보
 * 사건마다 Yes/No 하나이고, 이름은 {@value #EVENT_PREFIX} 에 사건 ID 를 붙인 것, 문장은 넘긴 문구로 만든다.
 */
public final class JevRequest {

	/** 주제 중분류 질문 이름의 접두 */
	public static final String TOPIC_PREFIX = "topic.";

	/** 지역 질문 이름의 접두 */
	public static final String REGION_PREFIX = "region.";

	/** 형식 질문 이름 */
	public static final String FORMAT = "format";

	/** 중요도 질문 이름 */
	public static final String IMPORTANCE = "importance";

	/** 사건 소속 질문 이름의 접두 */
	public static final String EVENT_PREFIX = "event.";

	/** state 의 출처 줄에서 조각 사이 */
	private static final String PART_SEPARATOR = " / ";

	/** state 의 출처 줄에서 카테고리 사이 */
	private static final String CATEGORY_SEPARATOR = ", ";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private JevRequest() {
	}

	/** 평가 요청 — state 는 {@link #state}, 질문은 체계의 모든 질문 */
	public static String body(String model, EvaluationInput input, TagSetView tagSet) {
		return write(model, state(input), tagSet);
	}

	/** 문맥 초과 확인 요청 — 같은 질문, 빈 state */
	public static String probeBody(String model, TagSetView tagSet) {
		return write(model, "", tagSet);
	}

	/** 사건 소속 판정 요청 — state 는 제목 줄 · 요약 줄, 질문은 후보 사건마다 Yes/No 하나(넘긴 순서) */
	public static String membershipBody(String model, MembershipInput input) {
		return write(model, String.join("\n", articleLines(input.title(), input.summary())), membershipQuestions(input));
	}

	/** 사건 소속 판정의 문맥 초과 확인 요청 — 같은 질문, 빈 state */
	public static String membershipProbeBody(String model, MembershipInput input) {
		return write(model, "", membershipQuestions(input));
	}

	/**
	 * state 문자열 — 피드 맥락마다 출처 줄 하나, 제목 줄, 요약 줄. 없는 값의 조각과 요약이 없을 때의 요약 줄은 뺀다. 조각이 하나도 없는 피드는
	 * 줄을 내지 않는다.
	 */
	public static String state(EvaluationInput input) {
		List<String> lines = new ArrayList<>();
		for (FeedContext feed : input.feeds()) {
			List<String> parts = new ArrayList<>();
			if (feed.sourceName() != null) {
				parts.add("출처: " + feed.sourceName());
			}
			if (feed.section() != null) {
				parts.add("피드 섹션: " + feed.section());
			}
			if (!feed.categories().isEmpty()) {
				parts.add("카테고리: " + String.join(CATEGORY_SEPARATOR, feed.categories()));
			}
			if (!parts.isEmpty()) {
				lines.add(String.join(PART_SEPARATOR, parts));
			}
		}
		lines.addAll(articleLines(input.title(), input.summary()));
		return String.join("\n", lines);
	}

	/** state 의 제목 줄과 요약 줄 — 요약이 없으면 요약 줄은 뺀다. 평가 · 판정의 state 가 함께 쓰는 정본 */
	private static List<String> articleLines(String title, @Nullable String summary) {
		return (summary == null) ? List.of("제목: " + title) : List.of("제목: " + title, "요약: " + summary);
	}

	private static Map<String, Object> membershipQuestions(MembershipInput input) {
		Map<String, Object> questions = new LinkedHashMap<>();
		for (CandidateEvent candidate : input.candidates()) {
			questions.put(EVENT_PREFIX + candidate.eventId(), yesNo(input.wording().question(candidate.titles())));
		}
		return questions;
	}

	private static String write(String model, String state, TagSetView tagSet) {
		Map<String, Object> questions = new LinkedHashMap<>();
		for (TopicCategory category : tagSet.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				questions.put(TOPIC_PREFIX + subtopic.key(), yesNo(subtopic.question()));
			}
		}
		for (AskedTag region : tagSet.regions()) {
			questions.put(REGION_PREFIX + region.key(), yesNo(region.question()));
		}
		Map<String, String> options = new LinkedHashMap<>();
		for (FormatOption option : tagSet.format().options()) {
			options.put(option.key(), option.description());
		}
		questions.put(FORMAT, question("choice", tagSet.format().question(), options));
		List<String> levels = tagSet.importance().levels().stream().map(ImportanceLevel::description).toList();
		questions.put(IMPORTANCE, question("score", tagSet.importance().question(), levels));
		return write(model, state, questions);
	}

	private static String write(String model, String state, Map<String, Object> questions) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("model", model);
		root.put("state", state);
		root.put("questions", questions);
		return JSON.writeValueAsString(root);
	}

	private static Map<String, Object> yesNo(String instructions) {
		Map<String, Object> question = new LinkedHashMap<>();
		question.put("type", "noul");
		question.put("instructions", instructions);
		return question;
	}

	private static Map<String, Object> question(String type, String instructions, Object criteria) {
		Map<String, Object> question = new LinkedHashMap<>();
		question.put("type", type);
		question.put("instructions", instructions);
		question.put("criteria", criteria);
		return question;
	}

}
