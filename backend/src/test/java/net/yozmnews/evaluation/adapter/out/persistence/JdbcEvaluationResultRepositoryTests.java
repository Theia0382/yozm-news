package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.StoredEvaluation;
import net.yozmnews.evaluation.domain.result.StoredTag;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 평가 결과 저장소 — 평가 · 태그 행과 {@code jsonb} 왕복, (기사, 버전) 고유, 최근 버전 조회, 지운 기사 따르기를 Flyway 가 적용된 실제
 * PostgreSQL 에서 본다.
 * <p>
 * 지키는 규칙: evaluation-R15 · evaluation-R21
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEvaluationResultRepository.class })
class JdbcEvaluationResultRepositoryTests {

	private static final Instant AT = Instant.parse("2026-11-02T00:00:00Z");

	@Autowired
	JdbcEvaluationResultRepository repository;

	@Autowired
	JdbcClient jdbc;

	TagSetId first;

	TagSetId second;

	@BeforeEach
	void createTagSets() {
		this.jdbc.sql("delete from tag_set_threshold_changes").update();
		this.jdbc.sql("delete from tag_set_thresholds").update();
		this.jdbc.sql("delete from importance_rubrics").update();
		this.jdbc.sql("delete from tags").update();
		this.jdbc.sql("delete from tag_sets").update();
		// 번호가 큰 버전을 먼저 만든다 — 행 ID 순과 버전 번호 순이 어긋난다
		this.second = tagSet(2, true);
		this.first = tagSet(1, false);
	}

	@Test
	void storesTheEvaluationWithItsTagsAndJsonDistributions() {
		this.repository.insert(record(42, this.first, 3.25, "{\"answers\": [1, 2]}",
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5),
						new StoredTag(MultiTagKind.REGION, "domestic", 0.75))));

		Map<String, Object> row = this.jdbc.sql("""
				select article_id, tag_set_id, evaluator, model_version, format_key, format_probability,
				    format_distribution::text as format_distribution, importance,
				    importance_distribution::text as importance_distribution, raw_response::text as raw_response,
				    input_tokens, evaluated_at
				from article_evaluations
				""").query().singleRow();
		assertThat(row).containsEntry("article_id", 42L)
			.containsEntry("tag_set_id", this.first.value())
			.containsEntry("evaluator", "jev")
			.containsEntry("model_version", "jev-1")
			.containsEntry("format_key", "straight")
			.containsEntry("format_probability", 0.5)
			.containsEntry("format_distribution", "{\"opinion\": 0.25, \"straight\": 0.5}")
			.containsEntry("importance", 3.25)
			.containsEntry("importance_distribution", "{\"1\": 0.25, \"5\": 0.75}")
			.containsEntry("raw_response", "{\"answers\": [1, 2]}")
			.containsEntry("input_tokens", 1700);
		assertThat(instant(row.get("evaluated_at"))).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
		assertThat(this.jdbc.sql("""
				select article_id, tag_kind, tag_key, probability from article_tags order by tag_kind, tag_key
				""").query().listOfRows()).containsExactly(
						Map.of("article_id", 42L, "tag_kind", "REGION", "tag_key", "domestic", "probability", 0.75),
						Map.of("article_id", 42L, "tag_kind", "TOPIC", "tag_key", "politics.assembly", "probability", 0.5));
	}

	@Test
	void allowsOneEvaluationPerArticleAndVersion() {
		this.repository.insert(record(42, this.first, 3.0, "{}", List.of()));

		assertThat(this.repository.exists(new ArticleId(42), this.first)).isTrue();
		assertThat(this.repository.exists(new ArticleId(42), this.second)).isFalse();
		assertThat(this.repository.exists(new ArticleId(43), this.first)).isFalse();
		assertThatExceptionOfType(DuplicateKeyException.class)
			.isThrownBy(() -> this.repository.insert(record(42, this.first, 3.0, "{}", List.of())));
	}

	@Test
	void findsTheEvaluationOfTheHighestVersionWithItsTags() {
		this.repository.insert(record(42, this.second, null, "{}",
				List.of(new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.625))));
		this.repository.insert(record(42, this.first, 3.0, "{}",
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5))));

		assertThat(this.repository.findLatest(new ArticleId(42))).contains(new StoredEvaluation(new ArticleId(42),
				this.second, "straight", null, List.of(new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.625))));
		assertThat(this.repository.findLatest(new ArticleId(43))).isEmpty();
	}

	@Test
	void deletesTheEvaluationsAndTagsOfTheGivenArticlesOnly() {
		this.repository.insert(record(42, this.first, 3.0, "{}",
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5))));
		this.repository.insert(record(43, this.first, 3.0, "{}",
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5))));

		this.repository.deleteByArticles(List.of(new ArticleId(42), new ArticleId(99)));
		this.repository.deleteByArticles(List.of());

		assertThat(this.jdbc.sql("select article_id from article_evaluations").query(Long.class).list())
			.containsExactly(43L);
		assertThat(this.jdbc.sql("select article_id from article_tags").query(Long.class).list()).containsExactly(43L);
	}

	// jsonb 가 받지 않는 원문 — 저장 전에 ResultCheck 가 걸러야 한다. 실패한 문장은 테스트 트랜잭션을 깨뜨리므로 원문마다 테스트를 나눈다
	@Test
	void postgresRejectsANulCharacterEscapeInTheRawResponse() {
		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> this.repository.insert(record(42, this.first, 3.0, "{\"a\": \"\\u0000\"}", List.of())))
			.withMessageContaining("\\u0000 cannot be converted to text");
	}

	@Test
	void postgresRejectsAnUnpairedSurrogateEscapeInTheRawResponse() {
		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> this.repository.insert(record(42, this.first, 3.0, "{\"a\": \"\\ud800\"}", List.of())))
			.withMessageContaining("surrogate");
	}

	@Test
	void postgresAcceptsAPairedSurrogateEscapeInTheRawResponse() {
		this.repository.insert(record(42, this.first, 3.0, "{\"a\": \"\\ud83d\\ude00\"}", List.of()));

		assertThat(this.jdbc.sql("select raw_response ->> 'a' from article_evaluations").query(String.class).single())
			.isEqualTo("\uD83D\uDE00");
	}

	private TagSetId tagSet(int number, boolean active) {
		return new TagSetId(this.jdbc.sql("""
				insert into tag_sets (number, active, topic_template, region_template, format_question,
				    importance_question, created_by, created_at)
				values (:number, :active, '{description}', '{description}', '형식', '중요도', 'SYSTEM', now())
				returning id
				""").param("number", number).param("active", active).query(Long.class).single());
	}

	private static EvaluationRecord record(long article, TagSetId version, @Nullable Double importance, String raw,
			List<StoredTag> tags) {
		return new EvaluationRecord(new ArticleId(article), version, "jev", "jev-1", "straight", 0.5,
				Map.of("straight", 0.5, "opinion", 0.25), importance, Map.of(5, 0.75, 1, 0.25), tags, raw, 1700, AT);
	}

	private static Instant instant(@Nullable Object timestamp) {
		return switch (timestamp) {
			case OffsetDateTime offset -> offset.toInstant();
			case java.sql.Timestamp sql -> sql.toInstant();
			case null, default -> throw new IllegalStateException("시각이 아니다: " + timestamp);
		};
	}

}
