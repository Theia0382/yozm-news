package net.yozmnews.evaluation.domain.evaluator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.evaluation.RelationAnswer;

/**
 * 관계 판정의 문구와 그 판 — docs/specs/evaluation.md §4 · evaluation-R48.
 * <p>
 * 요청의 state 는 두 흐름의 서술이고({@link #state}), 질문은 관계 하나와 흐름마다 한 사안인가다. 문구나 state · 질문의 조립 모양을 바꾸면
 * 판을 올린다 — 판은 판정 원본에 남아 바꾼 전후의 판정을 가른다.
 *
 * @param version 문구 판
 * @param question 관계 질문
 * @param options 답마다 선택지의 문장 — 답 다섯 모두
 * @param oneMatter 한 사안 질문의 틀 — {@value #FLOW_NAME} 자리에 흐름 이름이 들어간다
 */
public record RelationWording(String version, String question, Map<RelationAnswer, String> options,
		String oneMatter) {

	/** 한 사안 질문의 틀에서 흐름 이름이 들어갈 자리 */
	public static final String FLOW_NAME = "{flow}";

	/** 요청에서 first 를 부르는 이름 */
	public static final String FIRST_NAME = "A";

	/** 요청에서 second 를 부르는 이름 */
	public static final String SECOND_NAME = "B";

	/** 지금 문구 — 정본 */
	public static final RelationWording CURRENT = new RelationWording("relation-1",
			"What is the relation between flow A and flow B?",
			Map.of(RelationAnswer.SECOND_PART_OF_FIRST,
					"B is narrower than A and lies inside it: B covers one reaction to A, one stage of A, or one strand "
							+ "of A, and is still about the matter of A.",
					RelationAnswer.FIRST_PART_OF_SECOND,
					"A is narrower than B and lies inside it: A covers one reaction to B, one stage of B, or one strand "
							+ "of B, and is still about the matter of B.",
					RelationAnswer.FIRST_LED_TO_SECOND,
					"B is about a different matter from A. B has its own actors and its own course and would go on even "
							+ "if A ended, but it arose because of what happened in A.",
					RelationAnswer.SECOND_LED_TO_FIRST,
					"A is about a different matter from B. A has its own actors and its own course and would go on even "
							+ "if B ended, but it arose because of what happened in B.",
					RelationAnswer.NONE,
					"None of the above. A and B are separate matters, or they cover the same matter as a whole, or one "
							+ "of them is a collection of separate happenings rather than one matter. They may be of a "
							+ "similar kind, involve the same people, or share a cause, but neither is inside the other "
							+ "and neither arose from the other."),
			"Is flow {flow} the unfolding of one matter, rather than a collection of separate happenings that are only "
					+ "of a similar kind?");

	/** state 의 날짜 — UTC 의 월-일 */
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneOffset.UTC);

	/**
	 * @throws IllegalArgumentException 판 · 문장이 공백뿐이거나, 선택지가 답 다섯 모두가 아니거나, 한 사안 질문의 틀에 흐름 이름의 자리가
	 * 없을 때
	 */
	public RelationWording {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(question, "question");
		Objects.requireNonNull(options, "options");
		Objects.requireNonNull(oneMatter, "oneMatter");
		if (version.isBlank() || question.isBlank() || oneMatter.isBlank()) {
			throw new IllegalArgumentException("문구 판 · 질문이 비었다");
		}
		if (!options.keySet().equals(EnumSet.allOf(RelationAnswer.class))) {
			throw new IllegalArgumentException("선택지는 답 다섯 모두다: " + options.keySet());
		}
		for (String option : options.values()) {
			if (option.isBlank()) {
				throw new IllegalArgumentException("선택지의 문장이 비었다");
			}
		}
		if (!oneMatter.contains(FLOW_NAME)) {
			throw new IllegalArgumentException("한 사안 질문의 틀에 흐름 이름의 자리가 없다");
		}
		options = Collections.unmodifiableMap(new EnumMap<>(options));
	}

	/**
	 * 요청의 state — 정본. 흐름마다 머리 줄 하나와 서술 사건마다 제목 줄, 요약문을 실으면 그 다음 줄이다. 두 흐름 사이에 빈 줄을 둔다.
	 */
	public String state(FlowText first, FlowText second) {
		List<String> lines = new ArrayList<>(flow(FIRST_NAME, first));
		lines.add("");
		lines.addAll(flow(SECOND_NAME, second));
		return String.join("\n", lines);
	}

	/**
	 * 한 사안 질문의 문장 — 정본
	 * @param flowName 흐름 이름({@value #FIRST_NAME} · {@value #SECOND_NAME})
	 */
	public String oneMatterQuestion(String flowName) {
		return this.oneMatter.replace(FLOW_NAME, flowName);
	}

	private static List<String> flow(String name, FlowText flow) {
		List<String> lines = new ArrayList<>();
		lines.add("Flow " + name + " (" + flow.happenings() + " happenings, " + day(flow.firstAt()) + " to "
				+ day(flow.lastAt()) + "):");
		for (HappeningText happening : flow.described()) {
			lines.add("- [" + day(happening.at()) + "] " + happening.title());
			if (happening.summary() != null) {
				lines.add("  summary: " + happening.summary());
			}
		}
		return lines;
	}

	private static String day(Instant at) {
		return DAY.format(at);
	}

}
