package net.yozmnews.tag.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.TagAnswers;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TagReread;
import net.yozmnews.evaluation.TaggingJudgement;
import net.yozmnews.evaluation.TaggingQuery;
import net.yozmnews.evaluation.TopicCategory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 시험용 태그 판정 — 공개 API {@link ArticleTagging} 의 대역. 태그 쪽 시험이 판정 · 다시 읽기의 결과를 대본으로 정한다.
 * <p>
 * 판정은 부를 때마다 요청과 그때 트랜잭션이 있었는지를 기록하고 대본의 결과를 돌려준다 — 대본이 없으면 {@link #judged} 다. 다시 읽기는
 * 대본이 없으면 크게 실패한다. 원본 조회는 넘긴 판정(보통 결선된 운영 구현)에 맡긴다. 관문의 일(기사 읽기 · 질문 묶음과의 대조 · 원본
 * 응답 해석)은 하지 않는다 — 그것은 관문 시험이 본다.
 */
public final class ScriptedTagging implements ArticleTagging {

	/** 기본 답에서 모든 주제 중분류 · 지역의 확률 — 처음 저장 기준(0.1)에 저장된다 */
	public static final double DEFAULT_PROBABILITY = 0.5;

	/** 기본 판정 원본 ID 의 시작 — 평가 행의 판정 원본 ID 는 유일해서, 같은 DB 를 쓰는 시험의 실제 원본 ID 와 겹치지 않게 크게 둔다 */
	private static final AtomicLong IDS = new AtomicLong(1_000_000_000L);

	private final ArticleTagging finder;

	private final List<Call> calls = new CopyOnWriteArrayList<>();

	private Function<TaggingQuery, TaggingJudgement> judge = query -> judged(query.questions());

	private BiFunction<TagJudgementId, TagQuestions, TagReread> reread = (judgement, questions) -> {
		throw new AssertionError("다시 읽기 대본이 없다 — 원본 " + judgement.value());
	};

	/**
	 * @param finder 원본 조회를 맡길 판정 — 보통 결선된 운영 구현
	 */
	public ScriptedTagging(ArticleTagging finder) {
		this.finder = finder;
	}

	/** 판정 대본을 정한다 — 대본 안에서 부작용(기사 지우기 · 활성 전환 · 시계 옮기기)을 내도 된다 */
	public ScriptedTagging judging(Function<TaggingQuery, TaggingJudgement> script) {
		this.judge = script;
		return this;
	}

	/** 다시 읽기 대본을 정한다 */
	public ScriptedTagging rereading(BiFunction<TagJudgementId, TagQuestions, TagReread> script) {
		this.reread = script;
		return this;
	}

	@Override
	public TaggingJudgement judge(TaggingQuery query) {
		this.calls.add(new Call(query, TransactionSynchronizationManager.isActualTransactionActive()));
		return this.judge.apply(query);
	}

	@Override
	public TagReread reread(TagJudgementId judgement, TagQuestions questions) {
		return this.reread.apply(judgement, questions);
	}

	@Override
	public Map<TagJudgementId, TagJudgementView> find(Collection<TagJudgementId> judgements) {
		return this.finder.find(judgements);
	}

	/** 판정을 부른 차례대로 — 요청과 그때 트랜잭션이 있었는지 */
	public List<Call> calls() {
		return List.copyOf(this.calls);
	}

	/** 새 판정 원본 ID 와 {@link #answers(TagQuestions)} 로 판정했다 — 원본 행은 남기지 않는다 */
	public static TaggingJudgement judged(TagQuestions questions) {
		return judged(answers(questions));
	}

	/** 새 판정 원본 ID 와 넘긴 답으로 판정했다 — 원본 행은 남기지 않는다 */
	public static TaggingJudgement judged(TagAnswers answers) {
		return new TaggingJudgement.Judged(new TagJudgementId(IDS.incrementAndGet()), answers);
	}

	/** 기본 답 — 모든 주제 중분류 · 지역 {@value #DEFAULT_PROBABILITY} */
	public static TagAnswers answers(TagQuestions questions) {
		return answers(questions, DEFAULT_PROBABILITY, Map.of(), Map.of());
	}

	/**
	 * 질문 묶음의 모든 질문에 답한다 — 넘긴 주제(전체 키) · 지역의 확률, 나머지 주제 · 지역은 {@code others}. 형식은 첫 선택지 1.0 ·
	 * 나머지 0.0, 중요도는 첫 수준 1.0 · 나머지 0.0 이다.
	 */
	public static TagAnswers answers(TagQuestions questions, double others, Map<String, Double> topics,
			Map<String, Double> regions) {
		Map<String, Double> topicAnswers = new HashMap<>();
		for (TopicCategory category : questions.topics()) {
			for (AskedTag subtopic : category.subtopics()) {
				topicAnswers.put(subtopic.key(), others);
			}
		}
		topicAnswers.putAll(topics);
		Map<String, Double> regionAnswers = new HashMap<>();
		for (AskedTag region : questions.regions()) {
			regionAnswers.put(region.key(), others);
		}
		regionAnswers.putAll(regions);
		Map<String, Double> formats = new HashMap<>();
		List<FormatOption> options = questions.format().options();
		for (int i = 0; i < options.size(); i++) {
			formats.put(options.get(i).key(), (i == 0) ? 1.0 : 0.0);
		}
		Map<Integer, Double> importance = new HashMap<>();
		List<ImportanceLevel> levels = questions.importance().levels();
		for (int i = 0; i < levels.size(); i++) {
			importance.put(levels.get(i).level(), (i == 0) ? 1.0 : 0.0);
		}
		return new TagAnswers(topicAnswers, regionAnswers, formats, importance);
	}

	/**
	 * 판정 한 번의 기록.
	 *
	 * @param inTransaction 부를 때 실제 트랜잭션이 있었는지
	 */
	public record Call(TaggingQuery query, boolean inTransaction) {
	}

}
