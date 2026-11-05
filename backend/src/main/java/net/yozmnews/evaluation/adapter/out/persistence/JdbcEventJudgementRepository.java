package net.yozmnews.evaluation.adapter.out.persistence;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.membership.EventJudgementRepository;
import net.yozmnews.evaluation.domain.membership.JudgedCandidate;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link EventJudgementRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 물은 후보와 원본 응답은 {@code jsonb} 로 쓴다. 이 행을 잠그는 다른
 * 쓰기가 없어 지우기는 잠금 순서 없이 한 문장이다.
 */
@Repository
class JdbcEventJudgementRepository implements EventJudgementRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcClient jdbc;

	JdbcEventJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void insert(MembershipRecord record) {
		this.jdbc.sql("""
				insert into article_event_judgements (article_id, evaluator, model_version, wording_version, candidates,
				    raw_response, input_tokens, judged_at)
				values (:article, :evaluator, :modelVersion, :wordingVersion, cast(:candidates as jsonb),
				    cast(:rawResponse as jsonb), :inputTokens, :judgedAt)
				""")
			.param("article", record.articleId().value())
			.param("evaluator", record.evaluator())
			.param("modelVersion", record.modelVersion())
			.param("wordingVersion", record.wordingVersion())
			.param("candidates", candidates(record.candidates()))
			.param("rawResponse", record.rawResponse())
			.param("inputTokens", record.inputTokens())
			.param("judgedAt", record.judgedAt().atOffset(ZoneOffset.UTC))
			.update();
	}

	@Override
	public void deleteByArticles(Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
		this.jdbc.sql("delete from article_event_judgements where article_id = any(:ids)").param("ids", ids).update();
	}

	/** 물은 후보의 jsonb — 후보마다 {@code eventId} · {@code articleIds} · {@code probability}, 요청 순서 */
	private static String candidates(List<JudgedCandidate> candidates) {
		return JSON.writeValueAsString(candidates.stream().map(candidate -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("eventId", candidate.eventId());
			row.put("articleIds", candidate.articleIds().stream().map(ArticleId::value).toList());
			row.put("probability", candidate.probability());
			return row;
		}).toList());
	}

}
