package net.yozmnews.evaluation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.application.TagSetRows;
import net.yozmnews.evaluation.domain.overview.EvaluationSample;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 평가 결과 저장소 — 평가 · 태그 행과 {@code jsonb} 왕복, (기사, 버전) 고유, 최근 버전 조회, 버전 · 기간 흘려 받기, 첫 평가 시각으로 기사
 * 흘려 받기, 지운 기사 따르기를 Flyway 가 적용된 실제 PostgreSQL 에서 본다. 테스트는 테스트 트랜잭션 안에서 돈다 — 흘려 받기의 트랜잭션 밖
 * 갈래는 그 트랜잭션을 잠시 멈추고 부른다.
 * <p>
 * 지키는 규칙: evaluation-R15 · evaluation-R21 · evaluation-R29 · evaluation-R30
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

	@Autowired
	PlatformTransactionManager transactionManager;

	TagSetId first;

	TagSetId second;

	@BeforeEach
	void createTagSets() {
		TagSetRows.removeAll(this.jdbc);
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
	void streamsTheEvaluationsOfTheVersionWhoseEvaluationTimeIsInThePeriod() {
		Instant from = Instant.parse("2026-10-27T15:00:00Z");
		Instant to = Instant.parse("2026-11-03T15:00:00Z");
		this.repository.insert(record(41, this.first, Instant.parse("2026-10-27T14:59:59Z"), 1_000, List.of()));
		this.repository.insert(record(42, this.first, from, 1_100,
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5),
						new StoredTag(MultiTagKind.REGION, "domestic", 0.75),
						new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.25))));
		this.repository.insert(record(43, this.first, Instant.parse("2026-11-03T14:59:59Z"), 1_200, List.of()));
		this.repository.insert(record(44, this.first, to, 1_300, List.of()));
		this.repository.insert(record(42, this.second, Instant.parse("2026-10-30T00:00:00Z"), 1_400,
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5))));

		List<EvaluationSample> streamed = new ArrayList<>();
		this.repository.streamVersionPeriod(this.first, from, to, streamed::add);

		// 시작은 포함 · 끝은 제외, 다른 버전 제외. 태그 행은 평가마다 모이고 태그 없는 평가도 나온다
		assertThat(streamed).containsExactly(
				new EvaluationSample(new StoredEvaluation(new ArticleId(42), this.first, "straight", 3.0,
						List.of(new StoredTag(MultiTagKind.REGION, "domestic", 0.75),
								new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.25),
								new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5))),
						0.5, 1_100),
				new EvaluationSample(new StoredEvaluation(new ArticleId(43), this.first, "straight", 3.0, List.of()), 0.5,
						1_200));
	}

	@Test
	void streamsEachEvaluationOnceAcrossCursorBatches() {
		// 평가 700건 × 태그 행 2 = 1,400행 — 커서 한 묶음(1,000행)을 넘는다
		long evaluations = this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, evaluator, model_version, format_key,
				    format_probability, format_distribution, importance, importance_distribution, raw_response,
				    input_tokens, evaluated_at)
				select n, :version, 'jev', 'jev-1', 'straight', 0.5, '{}', 3.0, '{}', '{}', n, :at
				from generate_series(1, 700) n
				""").param("version", this.first.value()).param("at", AT.atOffset(java.time.ZoneOffset.UTC)).update();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				select e.id, e.article_id, k.kind, k.key, 0.5 from article_evaluations e
				cross join (values ('TOPIC', 'politics.assembly'), ('REGION', 'domestic')) as k(kind, key)
				""").update();

		List<EvaluationSample> streamed = new ArrayList<>();
		this.repository.streamVersionPeriod(this.first, AT, AT.plusSeconds(1), streamed::add);

		assertThat(evaluations).isEqualTo(700);
		assertThat(streamed).hasSize(700).allMatch(sample -> sample.evaluation().tags().size() == 2);
		assertThat(streamed.stream().map(sample -> sample.evaluation().articleId().value()).distinct()).hasSize(700);
	}

	@Test
	void streamsTheLatestEvaluationOfArticlesFirstEvaluatedInThePeriod() {
		// 51: 버전 1 을 10월 1일, 버전 2 를 11월 3일에 평가 — 재평가가 첫 평가 시각을 옮기지 않는다
		this.repository.insert(record(51, this.first, Instant.parse("2026-10-01T00:00:00Z"), 1_000,
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5))));
		this.repository.insert(record(51, this.second, Instant.parse("2026-11-03T00:00:00Z"), 1_000,
				List.of(new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.75),
						new StoredTag(MultiTagKind.REGION, "domestic", 0.75),
						new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.25))));
		// 52: 버전 2 만 11월 3일에
		this.repository.insert(record(52, this.second, Instant.parse("2026-11-03T00:00:00Z"), 1_000, List.of()));
		// 53: 두 버전을 같은 시각에 — 기사는 한 번
		this.repository.insert(record(53, this.first, Instant.parse("2026-10-05T00:00:00Z"), 1_000, List.of()));
		this.repository.insert(record(53, this.second, Instant.parse("2026-10-05T00:00:00Z"), 1_000, List.of()));

		List<StoredEvaluation> october = new ArrayList<>();
		this.repository.streamFirstEvaluatedIn(Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-01T00:00:00Z"), october::add);
		List<StoredEvaluation> thirdOfNovember = new ArrayList<>();
		this.repository.streamFirstEvaluatedIn(Instant.parse("2026-11-03T00:00:00Z"),
				Instant.parse("2026-11-04T00:00:00Z"), thirdOfNovember::add);

		// 최신 버전 평가와 그 주제 태그 행만(지역 행 없음), 기사 ID 순
		assertThat(october).containsExactly(
				new StoredEvaluation(new ArticleId(51), this.second, "straight", 3.0,
						List.of(new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.75),
								new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.25))),
				new StoredEvaluation(new ArticleId(53), this.second, "straight", 3.0, List.of()));
		// 51 은 11월 3일에 재평가됐지만 첫 평가가 10월이라 나오지 않는다(대조: 버전 2 만 가진 52 는 나온다)
		assertThat(thirdOfNovember)
			.containsExactly(new StoredEvaluation(new ArticleId(52), this.second, "straight", 3.0, List.of()));
	}

	@Test
	void streamingOutsideATransactionIsRefused() {
		TransactionTemplate suspended = new TransactionTemplate(this.transactionManager);
		suspended.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

		// 대조: 테스트 트랜잭션 안에서 부르는 다른 흘려 받기 테스트는 행을 받는다
		assertThatIllegalStateException().isThrownBy(() -> suspended.executeWithoutResult(
				status -> this.repository.streamVersionPeriod(this.first, AT, AT.plusSeconds(1), sample -> {
				})))
			.withMessageContaining("트랜잭션 안에서");
		assertThatIllegalStateException().isThrownBy(() -> suspended.executeWithoutResult(
				status -> this.repository.streamFirstEvaluatedIn(AT, AT.plusSeconds(1), evaluation -> {
				})))
			.withMessageContaining("트랜잭션 안에서");
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

	private static EvaluationRecord record(long article, TagSetId version, Instant at, int tokens,
			List<StoredTag> tags) {
		return new EvaluationRecord(new ArticleId(article), version, "jev", "jev-1", "straight", 0.5,
				Map.of("straight", 0.5, "opinion", 0.25), 3.0, Map.of(5, 0.75, 1, 0.25), tags, "{}", tokens, at);
	}

	private static Instant instant(@Nullable Object timestamp) {
		return switch (timestamp) {
			case OffsetDateTime offset -> offset.toInstant();
			case java.sql.Timestamp sql -> sql.toInstant();
			case null, default -> throw new IllegalStateException("시각이 아니다: " + timestamp);
		};
	}

}
