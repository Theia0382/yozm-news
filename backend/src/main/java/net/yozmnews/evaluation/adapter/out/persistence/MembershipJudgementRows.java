package net.yozmnews.evaluation.adapter.out.persistence;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 소속 판정 원본 표 둘(사건 소속 {@code article_event_judgements} · 스토리라인 소속 {@code storyline_membership_judgements})의 넣기 · 지우기
 * — 정본 · docs/specs/evaluation.md §4.
 * <p>
 * 두 표는 칸이 같고 물은 후보 {@code jsonb} 의 ID 키({@code eventId} · {@code candidateId})만 다르다. 표 이름과 키는 이 패키지의 상수만
 * 넘긴다 — 바깥 입력이 SQL 에 들어가지 않는다.
 */
final class MembershipJudgementRows {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private MembershipJudgementRows() {
	}

	/**
	 * 판정 원본 한 행을 넣는다.
	 * @param table 표 이름 — 이 패키지의 상수
	 * @param idKey 물은 후보의 ID 키 — 이 패키지의 상수
	 * @return 넣은 행의 ID
	 */
	static long insert(JdbcClient jdbc, String table, String idKey, MembershipRecord record) {
		return jdbc.sql("""
				insert into %s (article_id, evaluator, model_version, wording_version, candidates, raw_response,
				    input_tokens, judged_at)
				values (:article, :evaluator, :modelVersion, :wordingVersion, cast(:candidates as jsonb),
				    cast(:rawResponse as jsonb), :inputTokens, :judgedAt)
				returning id
				""".formatted(table))
			.param("article", record.articleId().value())
			.param("evaluator", record.evaluator())
			.param("modelVersion", record.modelVersion())
			.param("wordingVersion", record.wordingVersion())
			.param("candidates", candidates(idKey, record))
			.param("rawResponse", record.rawResponse())
			.param("inputTokens", record.inputTokens())
			.param("judgedAt", record.judgedAt().atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
	}

	/** 기사들의 판정 원본을 지운다 — 빈 입력이면 아무것도 하지 않는다 */
	static void deleteByArticles(JdbcClient jdbc, String table, Collection<ArticleId> articleIds) {
		if (articleIds.isEmpty()) {
			return;
		}
		Long[] ids = articleIds.stream().map(ArticleId::value).toArray(Long[]::new);
		jdbc.sql("delete from %s where article_id = any(:ids)".formatted(table)).param("ids", ids).update();
	}

	/** 물은 후보의 jsonb — 후보마다 {ID 키, {@code articleIds}, {@code probability}} 한 칸, 요청 순서 */
	private static String candidates(String idKey, MembershipRecord record) {
		List<Map<String, Object>> cells = record.candidates().stream().map(candidate -> {
			Map<String, Object> cell = new LinkedHashMap<>();
			cell.put(idKey, candidate.candidateId());
			cell.put("articleIds", candidate.describedArticles().stream().map(ArticleId::value).toList());
			cell.put("probability", candidate.probability());
			return cell;
		}).toList();
		return JSON.writeValueAsString(cells);
	}

}
