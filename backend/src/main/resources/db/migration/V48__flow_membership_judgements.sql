-- evaluation 모듈 — 흐름 소속 판정 원본(evaluation-R52).
--
-- article_id 와 candidates 안의 서술 기사 ID 는 article 모듈의 articles 를, 흐름 ID 는 부르는 모듈(storyline)의 스토리라인이나
-- 사건을 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다. 판정에 성공했을 때만 한 행을 쓴다. 같은 기사의 판정이 여러 번이면
-- 행도 여러 개다.
CREATE TABLE flow_membership_judgements (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- 판정할 기사
  article_id       BIGINT       NOT NULL,
  evaluator        TEXT         NOT NULL,
  model_version    TEXT         NOT NULL,
  -- 질문 문구의 판 — 문구를 바꾸면 판이 바뀐다
  wording_version  TEXT         NOT NULL,
  -- 물은 후보 — [{"flowId": 흐름 ID, "articleIds": [서술 기사 ID …], "probability": 확률}, …] 요청 순서
  candidates       JSONB        NOT NULL,
  raw_response     JSONB        NOT NULL,
  input_tokens     INTEGER      NOT NULL CHECK (input_tokens >= 0),
  judged_at        TIMESTAMPTZ  NOT NULL
);
-- 지운 기사 따르기가 판정할 기사로 지운다
CREATE INDEX flow_membership_judgements_article_idx ON flow_membership_judgements (article_id);
-- 보존 정리가 판정 시각으로 훑는다
CREATE INDEX flow_membership_judgements_judged_idx ON flow_membership_judgements (judged_at);
