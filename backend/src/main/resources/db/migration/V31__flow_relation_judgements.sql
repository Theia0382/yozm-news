-- evaluation 모듈 — 관계 판정 원본.
--
-- 두 흐름 ID 는 부르는 모듈(storyline)의 스토리라인을, described 안의 서술 기사 ID 는 article 모듈의 articles 를 가리키지만 FK 를
-- 두지 않는다 — 모듈 사이는 ID 로만 참조한다. 판정에 성공했을 때만 한 행을 쓴다. 같은 쌍의 판정이 여러 번이면 행도 여러 개다.
CREATE TABLE flow_relation_judgements (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 요청의 first(A) · second(B) 흐름
  first_flow_id    BIGINT       NOT NULL,
  second_flow_id   BIGINT       NOT NULL,
  -- 요청에 실은 서술 — {"first": [{"articleId": 기사 ID, "summarized": 요약문을 실었는가}, …], "second": […]} 요청 순서.
  -- 제목 · 요약문 텍스트는 남기지 않는다
  described        JSONB        NOT NULL,
  evaluator        TEXT         NOT NULL,
  model_version    TEXT         NOT NULL,
  -- 문구의 판 — 문구나 state · 질문의 조립 모양을 바꾸면 판이 바뀐다
  wording_version  TEXT         NOT NULL,
  raw_response     JSONB        NOT NULL,
  input_tokens     INTEGER      NOT NULL CHECK (input_tokens >= 0),
  judged_at        TIMESTAMPTZ  NOT NULL,
  CONSTRAINT flow_relation_judgements_distinct_flows CHECK (first_flow_id <> second_flow_id)
);
-- 보존 정리가 판정 시각으로 훑는다
CREATE INDEX flow_relation_judgements_judged_idx ON flow_relation_judgements (judged_at);
