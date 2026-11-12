package net.yozmnews.evaluation.adapter.out.fake;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FeedContext;
import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationResult;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 고정 규칙 평가기 — 로컬 대체물 · docs/specs/evaluation.md §4.
 * <p>
 * 외부 호출 없이 같은 입력에 같은 결과를 낸다. 조종 계약(실패 표지 · 이름 일치가 보는 입력 · 이름 · 버전)은 Spec 이 정본이다.
 * <p>
 * 이 클래스가 정본인 내부 규칙:
 * <ul>
 * <li>이름 일치 — 태그 · 대분류 이름을 {@code ·} 로 나눠 앞뒤 공백을 지운 조각 가운데 2자 이상인 것 하나가 일치 문자열에
 * 들어 있으면 일치한다</li>
 * <li>주제 — 중분류 이름이 일치하면 그 중분류 {@value #NAME_MATCH}. 대분류 이름이 일치하고 그 대분류에 일치한 중분류가 없으면 그
 * 대분류의 중분류 가운데 해시로 고른 하나 {@value #CATEGORY_MATCH}. 둘 다 없으면 전체 중분류 가운데 해시로 고른 하나
 * {@value #PICKED}. 나머지는 {@value #LOW_BOUND} 미만의 해시값</li>
 * <li>지역 — 이름이 일치하면 {@value #NAME_MATCH}, 하나도 없으면 질문 묶음 순서의 첫 지역 {@value #PICKED}, 나머지는
 * {@value #LOW_BOUND} 미만의 해시값</li>
 * <li>형식 — 이름이 일치한 첫 선택지, 없으면 질문 묶음 순서의 첫 선택지가 {@value #FORMAT_CHOSEN}, 나머지 선택지가 그 나머지를
 * 똑같이 나눈다</li>
 * <li>중요도 — 해시로 고른 수준이 {@value #IMPORTANCE_CHOSEN}, 나머지 수준이 그 나머지를 똑같이 나눈다</li>
 * <li>해시 — {@code SHA-256(제목 + "\n" + 구분자 + "\n" + 키)} 의 앞 8바이트를 0 이상 1 미만으로 옮긴 값. 해시로 고르면 해시값이
 * 가장 큰 키(같으면 질문 묶음 순서가 앞선 키)다</li>
 * <li>입력 토큰 — 제목 · 요약 · 출처 이름 · 섹션 · 카테고리와 체계의 질문 문장 · 형식 선택지 이름 · 중요도 기준의 코드 포인트 합을
 * 2로 나눠 올린 값. 실제 토큰 수가 아니다</li>
 * <li>원본 응답 — 평가기 이름 · 버전과 태그 종류별 확률을 키 순으로 담은 JSON. 다시 읽기는 넘긴 질문 묶음의 키마다 그 확률을 읽고,
 * 입력 토큰은 원본에 없어 0 이다</li>
 * <li>사건 소속 판정 — 조각은 제목을 문자 · 숫자가 아닌 문자로 나눈 {@value #MIN_PIECE_CODE_POINTS} 코드 포인트 이상의 조각(중복
 * 없음)이다. 판정할 기사 제목의 조각 집합 A 와 그 후보 서술 제목들의 조각 합집합 D 에 대해 확률은
 * {@code MEMBERSHIP_FLOOR + MEMBERSHIP_SPAN * |A ∩ D| / |A|} 를 이 순서(곱한 뒤 나누고 더한다)로 계산한 값이다
 * ({@value #MEMBERSHIP_FLOOR} · {@value #MEMBERSHIP_SPAN}). A 가 비면 {@value #MEMBERSHIP_FLOOR} 다</li>
 * <li>판정 입력 토큰 — 제목 · 요약과 후보마다 질문 문장의 코드 포인트 합을 2로 나눠 올린 값</li>
 * <li>판정 원본 응답 — 평가기 이름 · 버전과 사건 ID 순의 확률({@code events})을 담은 JSON</li>
 * <li>관계 판정 — 흐름의 조각은 그 흐름 서술 제목들의 조각 합집합이다. 두 흐름의 조각 집합 A · B 의 겹침은
 * {@code |A ∩ B| / min(|A|, |B|)} 이다(한쪽이 비면 0). 겹침이 0 이면 해당 없음이 {@value #RELATION_NONE} 이고 나머지 넷이 그
 * 나머지를 똑같이 나눈다. 겹침이 있으면 서술 사건이 적은 흐름이 많은 흐름의 부분이라는 답(같으면 second 가 first 의 부분)이
 * {@code RELATION_FLOOR + RELATION_SPAN * 겹침}({@value #RELATION_FLOOR} · {@value #RELATION_SPAN})이고 나머지 넷이 그 나머지를
 * 똑같이 나눈다</li>
 * <li>한 사안 확률 — 서술 제목이 하나면 {@value #ONE_MATTER_SINGLE}. 둘 이상이면 그 흐름의 다른 서술 제목과 조각을 하나라도 함께 가진
 * 제목의 비율 r 로 {@code ONE_MATTER_FLOOR + ONE_MATTER_SPAN * r}({@value #ONE_MATTER_FLOOR} · {@value #ONE_MATTER_SPAN})</li>
 * <li>관계 판정 입력 토큰 — state 와 관계 질문 · 선택지 다섯 · 한 사안 질문 둘의 코드 포인트 합을 2로 나눠 올린 값</li>
 * <li>관계 판정 원본 응답 — 평가기 이름 · 버전과 답 이름 순의 확률({@code relation}), 두 한 사안 확률({@code oneMatter})을 담은
 * JSON</li>
 * </ul>
 * 넘긴 질문 묶음은 검증된 체계에서 왔다 — 주제 · 중분류 · 지역이 하나 이상, 형식 선택지가 둘 이상이다.
 */
public final class FakeArticleEvaluator implements ArticleEvaluator {

	/** 평가기 이름 */
	public static final String NAME = "fake";

	/** 모델 버전 — 규칙을 바꾸면 올린다 */
	public static final String MODEL_VERSION = "fake-rules-2";

	/** 원본 응답의 키 — 쓰기와 다시 읽기가 함께 쓴다 */
	private static final String RAW_EVALUATOR = "evaluator";

	private static final String RAW_MODEL_VERSION = "modelVersion";

	private static final String RAW_TOPICS = "topics";

	private static final String RAW_REGIONS = "regions";

	private static final String RAW_FORMATS = "formats";

	private static final String RAW_IMPORTANCE = "importance";

	private static final String RAW_EVENTS = "events";

	private static final String RAW_RELATION = "relation";

	private static final String RAW_ONE_MATTER = "oneMatter";

	/** 이름이 일치한 태그의 확률 */
	static final double NAME_MATCH = 0.9;

	/** 대분류 이름만 일치했을 때 고른 중분류의 확률 */
	static final double CATEGORY_MATCH = 0.8;

	/** 일치가 없을 때 고른 주제 · 지역의 확률 */
	static final double PICKED = 0.7;

	/** 나머지 태그 확률의 상한(미만) — 저장 기준 기본값(0.1)을 조금 넘겨, 나머지 가운데 소수만 저장 기준 이상이 되게 한다 */
	static final double LOW_BOUND = 0.12;

	/** 고른 형식의 확률 */
	static final double FORMAT_CHOSEN = 0.8;

	/** 고른 중요도 수준의 확률 */
	static final double IMPORTANCE_CHOSEN = 0.6;

	/** 사건 소속 판정에서 조각 겹침이 없을 때의 확률 */
	static final double MEMBERSHIP_FLOOR = 0.05;

	/** 사건 소속 판정에서 조각이 모두 겹칠 때 더하는 확률 */
	static final double MEMBERSHIP_SPAN = 0.9;

	/** 관계 판정에서 조각 겹침이 없을 때 해당 없음의 확률 */
	static final double RELATION_NONE = 0.9;

	/** 관계 판정에서 조각이 겹칠 때 고른 답의 바닥 확률 */
	static final double RELATION_FLOOR = 0.5;

	/** 관계 판정에서 조각이 모두 겹칠 때 고른 답에 더하는 확률 */
	static final double RELATION_SPAN = 0.4;

	/** 서술 제목이 하나인 흐름의 한 사안 확률 */
	static final double ONE_MATTER_SINGLE = 0.9;

	/** 서술 제목끼리 겹침이 없는 흐름의 한 사안 확률 */
	static final double ONE_MATTER_FLOOR = 0.1;

	/** 서술 제목이 모두 다른 제목과 겹칠 때 더하는 한 사안 확률 */
	static final double ONE_MATTER_SPAN = 0.8;

	/** 실패 표지에 따른 실패의 문구 */
	static final String FAILURE_MESSAGE = "Fake 평가기 표지에 따른 실패";

	private static final String NAME_SEPARATOR = "·";

	private static final int MIN_PIECE_CODE_POINTS = 2;

	/** 판정 조각의 경계 — 문자 · 숫자가 아닌 문자 */
	private static final Pattern PIECE_BOUNDARY = Pattern.compile("[^\\p{L}\\p{N}]+");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Override
	public EvaluationResult evaluate(EvaluationInput input, TagQuestions questions) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(questions, "questions");
		String title = input.title();
		EvaluationFailure failure = failure(title);
		if (failure != null) {
			return failure;
		}
		String text = matchText(input);
		Map<String, Double> topics = topics(title, questions.topics(), text);
		Map<String, Double> regions = regions(title, questions.regions(), text);
		Map<String, Double> formats = formats(questions.format().options(), text);
		Map<Integer, Double> importance = importance(title, questions.importance().levels());
		return new Evaluation(topics, regions, formats, importance, NAME, MODEL_VERSION, tokens(input, questions),
				rawResponse(topics, regions, formats, importance));
	}

	/** 자기가 남긴 원본 응답을 넘긴 질문 묶음로 다시 읽는다 */
	@Override
	public RereadResult reread(String evaluator, String rawResponse, TagQuestions questions) {
		Objects.requireNonNull(evaluator, "evaluator");
		Objects.requireNonNull(rawResponse, "rawResponse");
		Objects.requireNonNull(questions, "questions");
		if (!NAME.equals(evaluator)) {
			return new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "Fake 가 남긴 원본 응답이 아니다 — 평가기 " + evaluator);
		}
		try {
			return reread(rawResponse, questions);
		}
		catch (Unreadable ex) {
			return new RereadFailure(RereadFailure.Kind.UNREADABLE, "Fake 원본 응답을 읽을 수 없다 — " + ex.getMessage());
		}
	}

	/** 판정할 기사 제목과 후보 서술 제목의 조각 겹침으로 판정한다 */
	@Override
	public MembershipResult judgeMembership(MembershipInput input) {
		Objects.requireNonNull(input, "input");
		EvaluationFailure failure = failure(input.title());
		if (failure != null) {
			return failure;
		}
		Set<String> asked = pieces(input.title());
		Map<Long, Double> probabilities = new LinkedHashMap<>();
		List<String> texts = new ArrayList<>();
		texts.add(input.title());
		if (input.summary() != null) {
			texts.add(input.summary());
		}
		for (CandidateEvent candidate : input.candidates()) {
			Set<String> described = new HashSet<>();
			candidate.titles().forEach(title -> described.addAll(pieces(title)));
			probabilities.put(candidate.eventId(), membership(asked, described));
			texts.add(input.wording().question(candidate.titles()));
		}
		Map<String, Object> raw = new LinkedHashMap<>();
		raw.put(RAW_EVALUATOR, NAME);
		raw.put(RAW_MODEL_VERSION, MODEL_VERSION);
		raw.put(RAW_EVENTS, new TreeMap<>(probabilities));
		return new Membership(probabilities, NAME, MODEL_VERSION, tokens(texts), JSON.writeValueAsString(raw));
	}

	/** 두 흐름 서술 제목의 조각 겹침으로 판정한다 — 실패 표지는 first 의 첫 서술 제목에서 본다 */
	@Override
	public RelationResult judgeRelation(RelationInput input) {
		Objects.requireNonNull(input, "input");
		EvaluationFailure failure = failure(input.first().described().getFirst().title());
		if (failure != null) {
			return failure;
		}
		Map<RelationAnswer, Double> probabilities = relation(input.first(), input.second());
		double firstOneMatter = oneMatter(input.first());
		double secondOneMatter = oneMatter(input.second());
		List<String> texts = new ArrayList<>();
		texts.add(input.wording().state(input.first(), input.second()));
		texts.add(input.wording().question());
		texts.addAll(input.wording().options().values());
		texts.add(input.wording().oneMatterQuestion(RelationWording.FIRST_NAME));
		texts.add(input.wording().oneMatterQuestion(RelationWording.SECOND_NAME));
		Map<String, Object> raw = new LinkedHashMap<>();
		raw.put(RAW_EVALUATOR, NAME);
		raw.put(RAW_MODEL_VERSION, MODEL_VERSION);
		Map<String, Double> byName = new TreeMap<>();
		probabilities.forEach((answer, probability) -> byName.put(answer.name(), probability));
		raw.put(RAW_RELATION, byName);
		raw.put(RAW_ONE_MATTER, List.of(firstOneMatter, secondOneMatter));
		return new Relation(probabilities, firstOneMatter, secondOneMatter, NAME, MODEL_VERSION, tokens(texts),
				JSON.writeValueAsString(raw));
	}

	/** 답 다섯의 확률 — 겹침이 없으면 해당 없음, 있으면 서술 사건이 적은 흐름이 많은 흐름의 부분 */
	private static Map<RelationAnswer, Double> relation(FlowText first, FlowText second) {
		Set<String> a = flowPieces(first);
		Set<String> b = flowPieces(second);
		long shared = a.stream().filter(b::contains).count();
		RelationAnswer chosen;
		double probability;
		if (shared == 0) {
			chosen = RelationAnswer.NONE;
			probability = RELATION_NONE;
		}
		else {
			chosen = (first.described().size() < second.described().size()) ? RelationAnswer.FIRST_PART_OF_SECOND
					: RelationAnswer.SECOND_PART_OF_FIRST;
			probability = RELATION_FLOOR + RELATION_SPAN * shared / Math.min(a.size(), b.size());
		}
		Map<RelationAnswer, Double> probabilities = new EnumMap<>(RelationAnswer.class);
		RelationAnswer[] answers = RelationAnswer.values();
		for (RelationAnswer answer : answers) {
			probabilities.put(answer, (answer == chosen) ? probability : (1 - probability) / (answers.length - 1));
		}
		return probabilities;
	}

	/** 그 흐름이 한 사안일 확률 — 다른 서술 제목과 조각을 함께 가진 제목의 비율로 */
	private static double oneMatter(FlowText flow) {
		List<Set<String>> titles = flow.described().stream().map(HappeningText::title).map(FakeArticleEvaluator::pieces).toList();
		if (titles.size() == 1) {
			return ONE_MATTER_SINGLE;
		}
		long linked = 0;
		for (int i = 0; i < titles.size(); i++) {
			for (int j = 0; j < titles.size(); j++) {
				if (i != j && titles.get(i).stream().anyMatch(titles.get(j)::contains)) {
					linked++;
					break;
				}
			}
		}
		return ONE_MATTER_FLOOR + ONE_MATTER_SPAN * linked / titles.size();
	}

	private static Set<String> flowPieces(FlowText flow) {
		Set<String> pieces = new HashSet<>();
		flow.described().forEach(happening -> pieces.addAll(pieces(happening.title())));
		return pieces;
	}

	/** 제목의 첫 실패 표지(갈래 선언 순서)에 따른 실패 — 없으면 null */
	private static @Nullable EvaluationFailure failure(String title) {
		for (EvaluatorFailureKind kind : EvaluatorFailureKind.values()) {
			if (title.contains(marker(kind))) {
				return new EvaluationFailure(kind, FAILURE_MESSAGE);
			}
		}
		return null;
	}

	/** {@code MEMBERSHIP_FLOOR + MEMBERSHIP_SPAN * |A ∩ D| / |A|} — 이 순서로 계산한다 */
	private static double membership(Set<String> asked, Set<String> described) {
		if (asked.isEmpty()) {
			return MEMBERSHIP_FLOOR;
		}
		long shared = asked.stream().filter(described::contains).count();
		return MEMBERSHIP_FLOOR + MEMBERSHIP_SPAN * shared / asked.size();
	}

	/** 제목의 판정 조각 — 문자 · 숫자가 아닌 문자로 나눈 {@value #MIN_PIECE_CODE_POINTS} 코드 포인트 이상의 조각 */
	private static Set<String> pieces(String title) {
		Set<String> pieces = new HashSet<>();
		for (String piece : PIECE_BOUNDARY.split(title)) {
			if (piece.codePointCount(0, piece.length()) >= MIN_PIECE_CODE_POINTS) {
				pieces.add(piece);
			}
		}
		return pieces;
	}

	/** 실패 갈래의 표지 — docs/specs/evaluation.md §4 */
	static String marker(EvaluatorFailureKind kind) {
		String name = switch (kind) {
			case TRANSIENT -> "transient";
			case ARTICLE_REJECTED -> "article-rejected";
			case EVALUATOR_UNAVAILABLE -> "evaluator-unavailable";
		};
		return "[fake:" + name + "]";
	}

	/** 이름 일치를 볼 문자열 — 보는 입력은 docs/specs/evaluation.md §4 */
	private static String matchText(EvaluationInput input) {
		List<String> parts = new ArrayList<>();
		parts.add(input.title());
		for (FeedContext feed : input.feeds()) {
			if (feed.section() != null) {
				parts.add(feed.section());
			}
			parts.addAll(feed.categories());
		}
		return String.join("\n", parts);
	}

	private static boolean matches(String name, String text) {
		for (String piece : name.split(NAME_SEPARATOR)) {
			String stripped = piece.strip();
			if (stripped.codePointCount(0, stripped.length()) >= MIN_PIECE_CODE_POINTS && text.contains(stripped)) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, Double> topics(String title, List<TopicCategory> categories, String text) {
		Map<String, Double> result = new LinkedHashMap<>();
		List<String> all = new ArrayList<>();
		for (TopicCategory category : categories) {
			for (AskedTag subtopic : category.subtopics()) {
				result.put(subtopic.key(), low(title, subtopic.key()));
				all.add(subtopic.key());
			}
		}
		boolean matched = false;
		for (TopicCategory category : categories) {
			boolean subtopicMatched = false;
			for (AskedTag subtopic : category.subtopics()) {
				if (matches(subtopic.name(), text)) {
					result.put(subtopic.key(), NAME_MATCH);
					subtopicMatched = true;
				}
			}
			if (!subtopicMatched && matches(category.name(), text)) {
				result.put(pick(title, "topic", category.subtopics().stream().map(AskedTag::key).toList()),
						CATEGORY_MATCH);
				matched = true;
			}
			matched |= subtopicMatched;
		}
		if (!matched) {
			result.put(pick(title, "topic", all), PICKED);
		}
		return result;
	}

	private static Map<String, Double> regions(String title, List<AskedTag> regions, String text) {
		Map<String, Double> result = new LinkedHashMap<>();
		boolean matched = false;
		for (AskedTag region : regions) {
			if (matches(region.name(), text)) {
				result.put(region.key(), NAME_MATCH);
				matched = true;
			}
			else {
				result.put(region.key(), low(title, region.key()));
			}
		}
		if (!matched) {
			result.put(regions.getFirst().key(), PICKED);
		}
		return result;
	}

	private static Map<String, Double> formats(List<FormatOption> options, String text) {
		String chosen = options.stream()
			.filter(option -> matches(option.name(), text))
			.map(FormatOption::key)
			.findFirst()
			.orElse(options.getFirst().key());
		double rest = (1 - FORMAT_CHOSEN) / (options.size() - 1);
		Map<String, Double> result = new LinkedHashMap<>();
		for (FormatOption option : options) {
			result.put(option.key(), option.key().equals(chosen) ? FORMAT_CHOSEN : rest);
		}
		return result;
	}

	private static Map<Integer, Double> importance(String title, List<ImportanceLevel> levels) {
		String chosen = pick(title, "importance",
				levels.stream().map(level -> Integer.toString(level.level())).toList());
		double rest = (1 - IMPORTANCE_CHOSEN) / (levels.size() - 1);
		Map<Integer, Double> result = new LinkedHashMap<>();
		for (ImportanceLevel level : levels) {
			result.put(level.level(), Integer.toString(level.level()).equals(chosen) ? IMPORTANCE_CHOSEN : rest);
		}
		return result;
	}

	private static int tokens(EvaluationInput input, TagQuestions questions) {
		List<String> texts = new ArrayList<>();
		texts.add(input.title());
		if (input.summary() != null) {
			texts.add(input.summary());
		}
		for (FeedContext feed : input.feeds()) {
			if (feed.sourceName() != null) {
				texts.add(feed.sourceName());
			}
			if (feed.section() != null) {
				texts.add(feed.section());
			}
			texts.addAll(feed.categories());
		}
		questions.topics().forEach(category -> category.subtopics().forEach(subtopic -> texts.add(subtopic.question())));
		questions.regions().forEach(region -> texts.add(region.question()));
		texts.add(questions.format().question());
		questions.format().options().forEach(option -> texts.add(option.name()));
		texts.add(questions.importance().question());
		questions.importance().levels().forEach(level -> texts.add(level.description()));
		return tokens(texts);
	}

	/** 글 목록의 코드 포인트 합을 2로 나눠 올린 값 — 평가 · 판정이 함께 쓴다 */
	private static int tokens(List<String> texts) {
		long codePoints = texts.stream().mapToLong(text -> text.codePointCount(0, text.length())).sum();
		return Math.toIntExact((codePoints + 1) / 2);
	}

	private static String rawResponse(Map<String, Double> topics, Map<String, Double> regions,
			Map<String, Double> formats, Map<Integer, Double> importance) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put(RAW_EVALUATOR, NAME);
		root.put(RAW_MODEL_VERSION, MODEL_VERSION);
		root.put(RAW_TOPICS, new TreeMap<>(topics));
		root.put(RAW_REGIONS, new TreeMap<>(regions));
		root.put(RAW_FORMATS, new TreeMap<>(formats));
		root.put(RAW_IMPORTANCE, new TreeMap<>(importance));
		return JSON.writeValueAsString(root);
	}

	/** 원본 응답에서 넘긴 질문 묶음의 키마다 확률을 읽는다 — 중요도 키는 수준의 문자열 */
	private static Evaluation reread(String rawResponse, TagQuestions questions) {
		JsonNode root;
		try {
			root = JSON.readTree(rawResponse);
		}
		catch (JacksonException ex) {
			throw new Unreadable("JSON 이 아니다");
		}
		if (root == null || !root.isObject()) {
			throw new Unreadable("JSON 객체가 아니다");
		}
		JsonNode model = root.get(RAW_MODEL_VERSION);
		if (model == null || !model.isString() || model.stringValue().isBlank()) {
			throw new Unreadable(RAW_MODEL_VERSION + " 이 없거나 비었다");
		}
		Map<String, Double> topics = new LinkedHashMap<>();
		for (TopicCategory category : questions.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				topics.put(subtopic.key(), probability(root, RAW_TOPICS, subtopic.key()));
			}
		}
		Map<String, Double> regions = new LinkedHashMap<>();
		for (AskedTag region : questions.regions()) {
			regions.put(region.key(), probability(root, RAW_REGIONS, region.key()));
		}
		Map<String, Double> formats = new LinkedHashMap<>();
		for (FormatOption option : questions.format().options()) {
			formats.put(option.key(), probability(root, RAW_FORMATS, option.key()));
		}
		Map<Integer, Double> importance = new LinkedHashMap<>();
		for (ImportanceLevel level : questions.importance().levels()) {
			importance.put(level.level(), probability(root, RAW_IMPORTANCE, Integer.toString(level.level())));
		}
		// 입력 토큰은 원본 응답에 없다 — 다시 읽기는 토큰 수를 쓰지 않는다
		return new Evaluation(topics, regions, formats, importance, NAME, model.stringValue(), 0, rawResponse);
	}

	private static double probability(JsonNode root, String kind, String key) {
		JsonNode values = root.get(kind);
		JsonNode value = (values != null && values.isObject()) ? values.get(key) : null;
		if (value == null || !value.isNumber()) {
			throw new Unreadable("확률이 없거나 숫자가 아니다 — " + kind + "." + key);
		}
		double probability = value.doubleValue();
		if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
			throw new Unreadable("확률이 0 ~ 1 이 아니다 — " + kind + "." + key);
		}
		return probability;
	}

	/** 해시값이 가장 큰 키 — 같으면 앞선 키 */
	private static String pick(String title, String separator, List<String> keys) {
		String best = keys.getFirst();
		double bestValue = hash(title, separator, best);
		for (String key : keys.subList(1, keys.size())) {
			double value = hash(title, separator, key);
			if (value > bestValue) {
				best = key;
				bestValue = value;
			}
		}
		return best;
	}

	private static double low(String title, String key) {
		return hash(title, "low", key) * LOW_BOUND;
	}

	/** {@code SHA-256(제목 + "\n" + 구분자 + "\n" + 키)} 의 앞 8바이트 → 0 이상 1 미만 */
	private static double hash(String title, String separator, String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest((title + "\n" + separator + "\n" + key).getBytes(StandardCharsets.UTF_8));
			return (ByteBuffer.wrap(digest).getLong() >>> 11) * 0x1.0p-53;
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없다", ex);
		}
	}

	/** 원본 응답을 읽을 수 없는 까닭 — 문구는 체계 키와 고정 문구만 담는다 */
	private static final class Unreadable extends RuntimeException {

		Unreadable(String message) {
			super(message, null, false, false);
		}

	}

}
