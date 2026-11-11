package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagJudgementView;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.jev.JevResponse;
import net.yozmnews.evaluation.domain.tagging.StoredTagJudgement;
import net.yozmnews.evaluation.domain.tagging.TagJudgementRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 태그 판정 원본 저장소 — 넣기 · 다시 읽기 · 조회 · 지우기와 원본 응답의 {@code jsonb} 왕복을 Flyway 가 적용된 실제 PostgreSQL 에서
 * 본다. 테스트는 테스트 트랜잭션 안에서 돈다.
 * <p>
 * 지키는 규칙: evaluation-R41 · evaluation-R42 · evaluation-R44
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcTagJudgementRepository.class })
class JdbcTagJudgementRepositoryTests {

	private static final Instant AT = Instant.parse("2026-11-02T00:00:00Z");

	/** jsonb 왕복 시험의 질문 묶음 — 주제 · 지역 하나씩, 형식 둘, 중요도 둘 */
	private static final TagQuestions ROUND_TRIP_QUESTIONS = new TagQuestions(
			List.of(new TopicCategory("politics", "정치", "정치",
					List.of(new AskedTag("politics.assembly", "국회", "국회", "국회를 다루는가")))),
			List.of(new AskedTag("domestic", "국내", "국내", "국내의 일인가")),
			new FormatChoice("형식은", List.of(new FormatOption("straight", "스트레이트", "사실", false),
					new FormatOption("opinion", "오피니언", "의견", true))),
			new ImportanceScale("중요도는", List.of(new ImportanceLevel(1, "작다"), new ImportanceLevel(2, "크다"))));

	@Autowired
	JdbcTagJudgementRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void storesAJudgementAndReadsItBackById() {
		TagJudgementId id = this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "jev", "jev-1.14.0", "{\"answers\": [1, 2]}", 1_700, AT));

		Map<String, Object> row = this.jdbc.sql("""
				select id, article_id, evaluator, model_version, raw_response::text as raw_response, input_tokens
				from article_tag_judgements
				""").query().singleRow();
		assertThat(row).containsEntry("id", id.value())
			.containsEntry("article_id", 42L)
			.containsEntry("evaluator", "jev")
			.containsEntry("model_version", "jev-1.14.0")
			.containsEntry("raw_response", "{\"answers\": [1, 2]}")
			.containsEntry("input_tokens", 1_700);
		assertThat(this.repository.find(id)).contains(new StoredTagJudgement("jev", "{\"answers\": [1, 2]}"));
		assertThat(this.repository.views(List.of(id))).isEqualTo(
				Map.of(id, new TagJudgementView(id, new ArticleId(42), "jev", "jev-1.14.0", 1_700, AT)));
	}

	@Test
	void missingIdsAreLeftOutOfTheViewsAndTheRead() {
		TagJudgementId stored = this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "fake", "fake-rules-2", "{}", 10, AT));
		TagJudgementId missing = new TagJudgementId(stored.value() + 1);

		assertThat(this.repository.views(List.of(stored, missing))).containsOnlyKeys(stored);
		assertThat(this.repository.views(List.of())).isEmpty();
		assertThat(this.repository.find(missing)).isEmpty();
	}

	@Test
	void deletesOneJudgementOrTheJudgementsOfArticles() {
		TagJudgementId first = this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "fake", "fake-rules-2", "{}", 10, AT));
		TagJudgementId second = this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "fake", "fake-rules-2", "{}", 10, AT));
		TagJudgementId other = this.repository
			.insert(new TagJudgementRecord(new ArticleId(43), "fake", "fake-rules-2", "{}", 10, AT));

		this.repository.delete(first);
		assertThat(this.repository.views(List.of(first, second, other))).containsOnlyKeys(second, other);

		this.repository.deleteByArticles(List.of(new ArticleId(42)));
		this.repository.deleteByArticles(List.of());
		assertThat(this.repository.views(List.of(first, second, other))).containsOnlyKeys(other);
	}

	@Test
	void theStoredRawResponseIsReadBackToTheSameProbabilities() {
		// jsonb 는 공백 · 키 순서를 바꾸지만 수치는 numeric 으로 자릿수를 지킨다 — 같은 파서가 같은 double 을 읽는다
		String raw = """
				{"model":"jev-1.14.0","answers":{\
				"topic.politics.assembly":{"type":"noul","noul":0.12345678901234568},\
				"region.domestic":{"type":"noul","noul":1.0E-5},\
				"format":{"type":"choice","probabilities":{"straight":0.75,"opinion":0.25}},\
				"importance":{"type":"score","probabilities":{"0":0.5,"1":0.5}}},\
				"usage":{"input_tokens":10}}""";
		TagJudgementId id = this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "jev", "jev-1.14.0", raw, 10, AT));

		String stored = this.repository.find(id).orElseThrow().rawResponse();

		assertThat(stored).isNotEqualTo(raw);
		Evaluation read = (Evaluation) JevResponse.read(stored, null, ROUND_TRIP_QUESTIONS);
		assertThat(read.topics()).isEqualTo(Map.of("politics.assembly", 0.12345678901234568));
		assertThat(read.regions()).isEqualTo(Map.of("domestic", 1.0E-5));
	}

	// jsonb 가 받지 않는 원문 — 저장 전에 ResultCheck 가 걸러야 한다. 실패한 문장은 테스트 트랜잭션을 깨뜨리므로 원문마다 테스트를 나눈다
	@Test
	void postgresRejectsANulCharacterEscapeInTheRawResponse() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "jev", "jev-1", "{\"a\": \"\\u0000\"}", 10, AT)))
			.withMessageContaining("\\u0000 cannot be converted to text");
	}

	@Test
	void postgresRejectsAnUnpairedSurrogateEscapeInTheRawResponse() {
		assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "jev", "jev-1", "{\"a\": \"\\ud800\"}", 10, AT)))
			.withMessageContaining("surrogate");
	}

	@Test
	void postgresAcceptsAPairedSurrogateEscapeInTheRawResponse() {
		this.repository
			.insert(new TagJudgementRecord(new ArticleId(42), "jev", "jev-1", "{\"a\": \"\\ud83d\\ude00\"}", 10, AT));

		assertThat(this.jdbc.sql("select raw_response ->> 'a' from article_tag_judgements").query(String.class).single())
			.isEqualTo("\uD83D\uDE00");
	}

}
