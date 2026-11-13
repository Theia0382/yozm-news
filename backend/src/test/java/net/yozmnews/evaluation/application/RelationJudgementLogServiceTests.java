package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.evaluation.RelationReading;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationRereadResult;
import net.yozmnews.evaluation.domain.evaluator.RelationResult;
import net.yozmnews.evaluation.domain.evaluator.RereadFailure;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import net.yozmnews.evaluation.domain.relation.RelationJudgementRepository;
import net.yozmnews.evaluation.domain.relation.RelationRecord;
import net.yozmnews.evaluation.domain.relation.StoredRelationJudgement;
import org.junit.jupiter.api.Test;

/**
 * 관계 판정 원본 조회 — 원본을 ID 로 찾아 평가기가 원본 응답을 다시 읽은 확률을 낸다. 평가기가 없거나 읽지 못하면 까닭과 함께 나머지 값을
 * 낸다. 저장소 · 평가기는 대역이다(포트 경계).
 * <p>
 * 지키는 규칙: evaluation-R50
 */
class RelationJudgementLogServiceTests {

	private static final Instant AT = Instant.parse("2026-11-05T06:30:00Z");

	private static final Map<RelationAnswer, Double> ANSWERS = Map.of(RelationAnswer.NONE, 0.1,
			RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.05,
			RelationAnswer.FIRST_LED_TO_SECOND, 0.1, RelationAnswer.SECOND_LED_TO_FIRST, 0.05);

	private static final StoredRelationJudgement STORED = new StoredRelationJudgement(new RelationJudgementId(41), 7, 9,
			"jev", "jev-1.14.0", "relation-1", 1924, "{\"stored\":true}", AT);

	@Test
	void readsTheProbabilitiesTheEvaluatorRereads() {
		Evaluator evaluator = new Evaluator((name, raw) -> new Relation(ANSWERS, 0.8, 0.6, name, "jev-1.14.0", 1924, raw));

		Map<RelationJudgementId, RelationJudgementView> found = new RelationJudgementLogService(() -> evaluator,
				new Repository(List.of(STORED)))
			.findAll(List.of(new RelationJudgementId(41)));

		assertThat(found).containsExactly(Map.entry(new RelationJudgementId(41),
				new RelationJudgementView(new RelationJudgementId(41), 7, 9, "jev", "jev-1.14.0", "relation-1", 1924, AT,
						new RelationReading.Read(ANSWERS, 0.8, 0.6))));
		assertThat(evaluator.asked).containsExactly("jev|{\"stored\":true}");
	}

	@Test
	void reportsNoEvaluatorWithTheOtherValues() {
		Map<RelationJudgementId, RelationJudgementView> found = new RelationJudgementLogService(() -> null,
				new Repository(List.of(STORED)))
			.findAll(List.of(new RelationJudgementId(41)));

		assertThat(found.get(new RelationJudgementId(41))).isEqualTo(new RelationJudgementView(new RelationJudgementId(41),
				7, 9, "jev", "jev-1.14.0", "relation-1", 1924, AT, new RelationReading.Unread(
						RelationReading.Unread.Reason.NO_EVALUATOR, "이 프로세스에 평가기가 없어 원본 응답을 읽지 못한다")));
	}

	@Test
	void passesOnTheReasonTheEvaluatorCouldNotRead() {
		Evaluator other = new Evaluator(
				(name, raw) -> new RereadFailure(RereadFailure.Kind.OTHER_EVALUATOR, "다른 평가기 — " + name));
		Evaluator unreadable = new Evaluator(
				(name, raw) -> new RereadFailure(RereadFailure.Kind.UNREADABLE, "읽을 수 없다"));

		assertThat(new RelationJudgementLogService(() -> other, new Repository(List.of(STORED)))
			.findAll(List.of(new RelationJudgementId(41)))
			.get(new RelationJudgementId(41))
			.reading())
			.isEqualTo(new RelationReading.Unread(RelationReading.Unread.Reason.OTHER_EVALUATOR, "다른 평가기 — jev"));
		assertThat(new RelationJudgementLogService(() -> unreadable, new Repository(List.of(STORED)))
			.findAll(List.of(new RelationJudgementId(41)))
			.get(new RelationJudgementId(41))
			.reading()).isEqualTo(new RelationReading.Unread(RelationReading.Unread.Reason.UNREADABLE, "읽을 수 없다"));
	}

	@Test
	void cannotReadARereadWithoutAllFiveAnswers() {
		Map<RelationAnswer, Double> four = Map.of(RelationAnswer.NONE, 0.1, RelationAnswer.SECOND_PART_OF_FIRST, 0.7,
				RelationAnswer.FIRST_PART_OF_SECOND, 0.05, RelationAnswer.FIRST_LED_TO_SECOND, 0.15);
		Evaluator evaluator = new Evaluator((name, raw) -> new Relation(four, 0.8, 0.6, name, "jev-1.14.0", 0, raw));

		RelationReading reading = new RelationJudgementLogService(() -> evaluator, new Repository(List.of(STORED)))
			.findAll(List.of(new RelationJudgementId(41)))
			.get(new RelationJudgementId(41))
			.reading();

		assertThat(reading).isInstanceOf(RelationReading.Unread.class);
		assertThat(((RelationReading.Unread) reading).reason()).isEqualTo(RelationReading.Unread.Reason.UNREADABLE);
	}

	@Test
	void leavesOutDeletedJudgementsAndAsksTheRepositoryOnce() {
		Repository repository = new Repository(List.of(STORED));

		Map<RelationJudgementId, RelationJudgementView> found = new RelationJudgementLogService(() -> null, repository)
			.findAll(List.of(new RelationJudgementId(41), new RelationJudgementId(42)));

		assertThat(found).containsOnlyKeys(new RelationJudgementId(41));
		assertThat(repository.asked).containsExactly(List.of(new RelationJudgementId(41), new RelationJudgementId(42)));
	}

	@Test
	void findsUpToTheLimitAndRefusesMore() {
		Repository repository = new Repository(List.of());
		RelationJudgementLogService service = new RelationJudgementLogService(() -> null, repository);
		List<RelationJudgementId> limit = IntStream.rangeClosed(1, 500).mapToObj(RelationJudgementId::new).toList();
		List<RelationJudgementId> over = IntStream.rangeClosed(1, 501).mapToObj(RelationJudgementId::new).toList();

		assertThat(service.findAll(limit)).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> service.findAll(over))
			.withMessage("관계 판정 원본은 한 번에 500건까지 찾는다: 501");
		assertThat(service.findAll(List.of())).isEmpty();
		assertThat(repository.asked).hasSize(1);
	}

	private static final class Repository implements RelationJudgementRepository {

		private final List<StoredRelationJudgement> rows;

		private final List<List<RelationJudgementId>> asked = new ArrayList<>();

		Repository(List<StoredRelationJudgement> rows) {
			this.rows = rows;
		}

		@Override
		public RelationJudgementId insert(RelationRecord record) {
			throw new AssertionError("넣지 않는다");
		}

		@Override
		public List<StoredRelationJudgement> findAll(Collection<RelationJudgementId> ids) {
			this.asked.add(List.copyOf(ids));
			return this.rows.stream().filter(row -> ids.contains(row.id())).toList();
		}

	}

	private static final class Evaluator implements ArticleEvaluator {

		private final BiFunction<String, String, RelationRereadResult> answer;

		private final List<String> asked = new ArrayList<>();

		Evaluator(BiFunction<String, String, RelationRereadResult> answer) {
			this.answer = answer;
		}

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagQuestions questions) {
			throw new AssertionError("평가하지 않는다");
		}

		@Override
		public RereadResult reread(String evaluator, String rawResponse, TagQuestions questions) {
			throw new AssertionError("태그 판정 원본을 다시 읽지 않는다");
		}

		@Override
		public MembershipResult judgeMembership(MembershipInput input) {
			throw new AssertionError("사건 소속을 판정하지 않는다");
		}

		@Override
		public RelationResult judgeRelation(RelationInput input) {
			throw new AssertionError("관계를 판정하지 않는다 — 다시 읽기만 한다");
		}

		@Override
		public RelationRereadResult rereadRelation(String evaluator, String rawResponse) {
			this.asked.add(evaluator + "|" + rawResponse);
			return this.answer.apply(evaluator, rawResponse);
		}

	}

}
