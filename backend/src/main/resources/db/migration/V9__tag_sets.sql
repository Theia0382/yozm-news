-- evaluation 모듈 — 태그 체계 버전 · 태그 · 중요도 기준과 주제 · 지역의 태그 기준 · 저장 기준, 그 변경 이력(docs/specs/evaluation.md §6).
--
-- 버전 내용(질문 틀 · 질문 · 태그 · 기준)은 만든 뒤 바꾸지 않는다(evaluation-R4). 두 기준만 바뀌고, 바뀔 때마다 이력을 추가한다
-- (evaluation-R11 · R13). 첫 버전은 기동 때 기본 체계 파일에서 들어간다(evaluation-R2). 태그 종류 · 행위자는 대문자 상수 이름이다.
CREATE TABLE tag_sets (
  id                       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  number                   INTEGER      NOT NULL UNIQUE CHECK (number >= 1),
  active                   BOOLEAN      NOT NULL,
  topic_template           TEXT         NOT NULL,
  region_template          TEXT         NOT NULL,
  format_question          TEXT         NOT NULL,
  importance_question      TEXT         NOT NULL,
  created_by               TEXT         NOT NULL CHECK (created_by IN ('SYSTEM', 'OPERATOR')),
  created_at               TIMESTAMPTZ  NOT NULL
);
-- 활성 버전은 하나 — evaluation-R3
CREATE UNIQUE INDEX tag_sets_single_active ON tag_sets ((true)) WHERE active;

CREATE TABLE tags (
  tag_set_id               BIGINT       NOT NULL REFERENCES tag_sets (id),
  kind                     TEXT         NOT NULL CHECK (kind IN ('TOPIC', 'REGION', 'FORMAT')),
  key                      TEXT         NOT NULL,
  parent_key               TEXT,
  name                     TEXT         NOT NULL,
  description              TEXT         NOT NULL,
  excluded_by_default      BOOLEAN      NOT NULL,
  sort_order               INTEGER      NOT NULL,
  PRIMARY KEY (tag_set_id, kind, key),
  -- 부모는 같은 버전 · 같은 태그 종류의 행. aggregate 의 자식 행 저장 순서가 정해지지 않아 커밋 때 확인한다
  FOREIGN KEY (tag_set_id, kind, parent_key)
    REFERENCES tags (tag_set_id, kind, key) DEFERRABLE INITIALLY DEFERRED,
  CHECK (parent_key IS NULL OR kind = 'TOPIC'),
  CHECK (NOT excluded_by_default OR kind = 'FORMAT')
);

CREATE TABLE importance_rubrics (
  tag_set_id               BIGINT       NOT NULL REFERENCES tag_sets (id),
  level                    INTEGER      NOT NULL CHECK (level BETWEEN 1 AND 5),
  description              TEXT         NOT NULL,
  PRIMARY KEY (tag_set_id, level)
);

CREATE TABLE tag_set_thresholds (
  tag_set_id               BIGINT       NOT NULL REFERENCES tag_sets (id),
  tag_kind                 TEXT         NOT NULL CHECK (tag_kind IN ('TOPIC', 'REGION')),
  tag_threshold            NUMERIC(4,3) NOT NULL CHECK (tag_threshold BETWEEN 0 AND 1),
  store_threshold          NUMERIC(4,3) NOT NULL CHECK (store_threshold BETWEEN 0 AND 1),
  PRIMARY KEY (tag_set_id, tag_kind)
);

-- 두 기준의 변경 이력 — 추가만 한다. 처음 값이 첫 행(이전 값 없음)
CREATE TABLE tag_set_threshold_changes (
  id                       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tag_set_id               BIGINT       NOT NULL REFERENCES tag_sets (id),
  tag_kind                 TEXT         NOT NULL CHECK (tag_kind IN ('TOPIC', 'REGION')),
  previous_tag_threshold   NUMERIC(4,3),
  previous_store_threshold NUMERIC(4,3),
  tag_threshold            NUMERIC(4,3) NOT NULL,
  store_threshold          NUMERIC(4,3) NOT NULL,
  actor                    TEXT         NOT NULL CHECK (actor IN ('SYSTEM', 'OPERATOR')),
  changed_at               TIMESTAMPTZ  NOT NULL,
  CHECK ((previous_tag_threshold IS NULL) = (previous_store_threshold IS NULL))
);
