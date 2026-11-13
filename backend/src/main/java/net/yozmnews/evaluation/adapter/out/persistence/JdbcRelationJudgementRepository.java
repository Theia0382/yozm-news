package net.yozmnews.evaluation.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.domain.relation.RelationJudgementRepository;
import net.yozmnews.evaluation.domain.relation.RelationRecord;
import net.yozmnews.evaluation.domain.relation.StoredRelationJudgement;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link RelationJudgementRepository} 의 JdbcClient 구현 — docs/specs/evaluation.md §4.
 * <p>
 * 판정 원본은 넣기 한 번이고 갱신하지 않는다 — aggregate 가 없다. 실은 서술과 원본 응답은 {@code jsonb} 로 쓰고, 읽을 때 원본 응답은
 * {@code raw_response::text} 로 꺼낸다 — 공백 · 키 순서는 원문과 다르고 수치는 자릿수를 지킨다.
 */
@Repository
class JdbcRelationJudgementRepository implements RelationJudgementRepository {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final JdbcClient jdbc;

	JdbcRelationJudgementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public RelationJudgementId insert(RelationRecord record) {
		long id = this.jdbc.sql("""
				insert into flow_relation_judgements (first_flow_id, second_flow_id, described, evaluator, model_version,
				    wording_version, raw_response, input_tokens, judged_at)
				values (:first, :second, cast(:described as jsonb), :evaluator, :modelVersion, :wordingVersion,
				    cast(:rawResponse as jsonb), :inputTokens, :judgedAt)
				returning id
				""")
			.param("first", record.firstFlowId())
			.param("second", record.secondFlowId())
			.param("described", described(record))
			.param("evaluator", record.evaluator())
			.param("modelVersion", record.modelVersion())
			.param("wordingVersion", record.wordingVersion())
			.param("rawResponse", record.rawResponse())
			.param("inputTokens", record.inputTokens())
			.param("judgedAt", record.judgedAt().atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		return new RelationJudgementId(id);
	}

	@Override
	public List<StoredRelationJudgement> findAll(Collection<RelationJudgementId> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		Long[] values = ids.stream().map(RelationJudgementId::value).distinct().toArray(Long[]::new);
		return this.jdbc.sql("""
				select id, first_flow_id, second_flow_id, evaluator, model_version, wording_version, input_tokens,
				    raw_response::text as raw_response, judged_at
				from flow_relation_judgements
				where id = any(:ids)
				order by id
				""")
			.param("ids", values)
			.query((rs, row) -> new StoredRelationJudgement(new RelationJudgementId(rs.getLong("id")),
					rs.getLong("first_flow_id"), rs.getLong("second_flow_id"), rs.getString("evaluator"),
					rs.getString("model_version"), rs.getString("wording_version"), rs.getInt("input_tokens"),
					rs.getString("raw_response"), rs.getObject("judged_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	/** {@code {"first": [{"articleId": …, "summarized": …}, …], "second": […]}} — 요청 순서 */
	private static String described(RelationRecord record) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("first", side(record.first()));
		root.put("second", side(record.second()));
		return JSON.writeValueAsString(root);
	}

	private static List<Map<String, Object>> side(List<RelationRecord.Described> described) {
		return described.stream().map(one -> {
			Map<String, Object> stored = new LinkedHashMap<>();
			stored.put("articleId", one.articleId().value());
			stored.put("summarized", one.summarized());
			return stored;
		}).toList();
	}

}
