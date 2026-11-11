-- evaluation 모듈 — 태그 판정 원본(docs/specs/evaluation.md §6 · evaluation-R41).
--
-- 태그 판정 한 번의 원본 응답 · 평가기 · 모델 버전 · 입력 토큰은 판정 원본으로 evaluation 이 남기고, 평가 행(article_evaluations)은
-- 그 ID 만 가리킨다. 이 마이그레이션 전의 평가는 평가 행에서 판정 원본으로 옮긴다 — 각 평가는 평가 ID 와 같은 ID 의 판정 원본을 받고,
-- 판정 시각은 평가 시각이다. 모듈 사이 참조라 FK 를 두지 않는다.

CREATE TABLE article_tag_judgements (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  article_id     BIGINT       NOT NULL,
  evaluator      TEXT         NOT NULL,
  model_version  TEXT         NOT NULL,
  raw_response   JSONB        NOT NULL,
  input_tokens   INTEGER      NOT NULL CHECK (input_tokens >= 0),
  judged_at      TIMESTAMPTZ  NOT NULL
);

INSERT INTO article_tag_judgements (id, article_id, evaluator, model_version, raw_response, input_tokens, judged_at)
OVERRIDING SYSTEM VALUE
SELECT id, article_id, evaluator, model_version, raw_response, input_tokens, evaluated_at
FROM article_evaluations;

-- 다음 판정 원본의 ID 는 옮긴 ID 의 최댓값 뒤부터다 — 옮긴 행이 없으면 1 부터
SELECT setval(pg_get_serial_sequence('article_tag_judgements', 'id'),
              COALESCE((SELECT max(id) FROM article_tag_judgements), 1),
              (SELECT count(*) > 0 FROM article_tag_judgements));

CREATE INDEX article_tag_judgements_article_idx ON article_tag_judgements (article_id);

ALTER TABLE article_evaluations ADD COLUMN judgement_id BIGINT;
UPDATE article_evaluations SET judgement_id = id;
ALTER TABLE article_evaluations ALTER COLUMN judgement_id SET NOT NULL;
ALTER TABLE article_evaluations ADD CONSTRAINT article_evaluations_judgement_key UNIQUE (judgement_id);
ALTER TABLE article_evaluations
  DROP COLUMN evaluator,
  DROP COLUMN model_version,
  DROP COLUMN raw_response,
  DROP COLUMN input_tokens;
