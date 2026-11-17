package net.yozmnews.evaluation.domain.evaluator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.evaluation.RelationAnswer;

/**
 * 관계 판정의 문구와 그 판 — docs/specs/evaluation.md §4 · evaluation-R48.
 * <p>
 * 요청의 state 는 두 흐름의 서술이고({@link #state}), 질문은 관계 하나와 흐름마다 한 사안인가다. 문구나 state · 질문의 조립 모양을 바꾸면
 * 판을 올린다 — 판은 판정 원본에 남아 바꾼 전후의 판정을 가른다. 판마다 답의 집합이 다를 수 있다 — 판정은 언제나 지금 판({@link #CURRENT})으로
 * 묻고, 옛 판은 그 판으로 남긴 판정 원본을 다시 읽을 때 쓴다({@link #ofVersion}).
 *
 * @param version 문구 판
 * @param question 관계 질문
 * @param options 답마다 선택지의 문장 — 이 판의 답 모두
 * @param order 요청에 선택지를 싣는 순서 — 이 판의 답 모두를 한 번씩. 답의 선언 순서(동률 순서)와는 다른 지식이다
 * @param oneMatter 한 사안 질문의 틀 — {@value #FLOW_NAME} 자리에 흐름 이름이 들어간다
 */
public record RelationWording(String version, String question, Map<RelationAnswer, String> options,
		List<RelationAnswer> order, String oneMatter) {

	/** 한 사안 질문의 틀에서 흐름 이름이 들어갈 자리 */
	public static final String FLOW_NAME = "{flow}";

	/** 요청에서 first 를 부르는 이름 */
	public static final String FIRST_NAME = "A";

	/** 요청에서 second 를 부르는 이름 */
	public static final String SECOND_NAME = "B";

	private static final String QUESTION = "What is the relation between flow A and flow B?";

	private static final String SECOND_PART_OF_FIRST = "B is narrower than A and lies inside it: B covers one reaction "
			+ "to A, one stage of A, or one strand of A, and is still about the matter of A.";

	private static final String FIRST_PART_OF_SECOND = "A is narrower than B and lies inside it: A covers one reaction "
			+ "to B, one stage of B, or one strand of B, and is still about the matter of B.";

	private static final String FIRST_LED_TO_SECOND = "B is about a different matter from A. B has its own actors and "
			+ "its own course and would go on even if A ended, but it arose because of what happened in A.";

	private static final String SECOND_LED_TO_FIRST = "A is about a different matter from B. A has its own actors and "
			+ "its own course and would go on even if B ended, but it arose because of what happened in B.";

	private static final String ONE_MATTER = "Is flow {flow} the unfolding of one matter, rather than a collection of "
			+ "separate happenings that are only of a similar kind?";

	/** 판 {@code relation-1} — 답 다섯. 같은 사안의 조각은 해당 없음에 든다. 이 판으로 남긴 판정 원본을 다시 읽을 때 쓴다 */
	public static final RelationWording RELATION_1 = new RelationWording("relation-1", QUESTION,
			Map.of(RelationAnswer.SECOND_PART_OF_FIRST, SECOND_PART_OF_FIRST, RelationAnswer.FIRST_PART_OF_SECOND,
					FIRST_PART_OF_SECOND, RelationAnswer.FIRST_LED_TO_SECOND, FIRST_LED_TO_SECOND,
					RelationAnswer.SECOND_LED_TO_FIRST, SECOND_LED_TO_FIRST, RelationAnswer.NONE,
					"None of the above. A and B are separate matters, or they cover the same matter as a whole, or one "
							+ "of them is a collection of separate happenings rather than one matter. They may be of a "
							+ "similar kind, involve the same people, or share a cause, but neither is inside the other "
							+ "and neither arose from the other."),
			List.of(RelationAnswer.SECOND_PART_OF_FIRST, RelationAnswer.FIRST_PART_OF_SECOND,
					RelationAnswer.FIRST_LED_TO_SECOND, RelationAnswer.SECOND_LED_TO_FIRST, RelationAnswer.NONE),
			ONE_MATTER);

	/** 판 {@code relation-2} — 답 여섯. 같은 사안의 조각을 따로 답한다 */
	public static final RelationWording RELATION_2 = new RelationWording("relation-2", QUESTION,
			Map.of(RelationAnswer.SECOND_PART_OF_FIRST, SECOND_PART_OF_FIRST, RelationAnswer.FIRST_PART_OF_SECOND,
					FIRST_PART_OF_SECOND, RelationAnswer.FIRST_LED_TO_SECOND, FIRST_LED_TO_SECOND,
					RelationAnswer.SECOND_LED_TO_FIRST, SECOND_LED_TO_FIRST, RelationAnswer.SAME_MATTER,
					"A and B are two parts of one and the same matter. Neither is narrower than the other and neither "
							+ "lies inside the other; taken together they follow that one matter.",
					RelationAnswer.NONE,
					"None of the above. A and B are separate matters, or one of them is a collection of separate "
							+ "happenings rather than one matter. They may be of a similar kind, involve the same "
							+ "people, or share a cause, but neither is inside the other and neither arose from the "
							+ "other."),
			List.of(RelationAnswer.SECOND_PART_OF_FIRST, RelationAnswer.FIRST_PART_OF_SECOND,
					RelationAnswer.FIRST_LED_TO_SECOND, RelationAnswer.SECOND_LED_TO_FIRST, RelationAnswer.SAME_MATTER,
					RelationAnswer.NONE),
			ONE_MATTER);

	/** 지금 문구 — 정본. 판정은 이 판으로 묻는다 */
	public static final RelationWording CURRENT = RELATION_2;

	/** 알려진 판 — 정본. 판정 원본에 남은 판은 이 가운데 하나다 */
	public static final List<RelationWording> KNOWN = List.of(RELATION_1, RELATION_2);

	/** state 의 날짜 — UTC 의 월-일 */
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneOffset.UTC);

	/**
	 * @throws IllegalArgumentException 판 · 문장이 공백뿐이거나, 선택지에 해당 없음이 없거나, 순서가 선택지의 답을 한 번씩 담지 않거나, 한
	 * 사안 질문의 틀에 흐름 이름의 자리가 없을 때
	 */
	public RelationWording {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(question, "question");
		Objects.requireNonNull(options, "options");
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(oneMatter, "oneMatter");
		if (version.isBlank() || question.isBlank() || oneMatter.isBlank()) {
			throw new IllegalArgumentException("문구 판 · 질문이 비었다");
		}
		if (!options.containsKey(RelationAnswer.NONE)) {
			throw new IllegalArgumentException("선택지에 해당 없음이 있어야 한다: " + options.keySet());
		}
		order = List.copyOf(order);
		if (order.size() != options.size() || !Set.copyOf(order).equals(options.keySet())) {
			throw new IllegalArgumentException("순서는 선택지의 답을 한 번씩 담는다: " + order + " · " + options.keySet());
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

	/** 그 판의 문구 — 모르는 판이면 빈 값 */
	public static Optional<RelationWording> ofVersion(String version) {
		return KNOWN.stream().filter(wording -> wording.version().equals(version)).findFirst();
	}

	/** 이 판의 답 */
	public Set<RelationAnswer> answers() {
		return this.options.keySet();
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
