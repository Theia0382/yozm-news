package net.yozmnews.evaluation.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import net.yozmnews.evaluation.TagJudgementId;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 픽스처 — 태그 판정 원본 행을 SQL 로 넣는다. 평가 행이 가리킬 원본과 원본 조회의 입력(평가기 · 모델 버전 · 입력 토큰 · 판정
 * 시각)을 주는 것이지 판정을 하는 것이 아니다 — 원본 응답은 해석하지 않고 그대로 넣는다.
 */
public final class TagJudgementRows {

	private TagJudgementRows() {
	}

	/** 판정 원본 하나를 넣는다 — DB 가 매긴 ID */
	public static TagJudgementId insert(JdbcClient jdbc, long article, String evaluator, String model, String raw,
			int tokens, Instant at) {
		return new TagJudgementId(jdbc.sql("""
				insert into article_tag_judgements (article_id, evaluator, model_version, raw_response, input_tokens,
				    judged_at)
				values (:article, :evaluator, :model, cast(:raw as jsonb), :tokens, :at)
				returning id
				""")
			.param("article", article)
			.param("evaluator", evaluator)
			.param("model", model)
			.param("raw", raw)
			.param("tokens", tokens)
			.param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
			.query(Long.class)
			.single());
	}

}
