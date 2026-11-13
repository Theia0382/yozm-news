-- storyline 모듈 — 큰 판정 기준이 보도 강도의 하한 하나로 바뀐다(docs/specs/storyline.md §6 · storyline-R20).
-- 옛 기준(소스 수 · 중요도 평균)의 값과 이력은 옮기지 않는다 — 처음 값은 기동 때 앱이 넣는다(정본은 코드의 기본값)

DROP TABLE storyline_big_criteria_changes;
DROP TABLE storyline_big_criteria;

-- 큰 판정 기준 — 지금 값 한 행
CREATE TABLE storyline_big_criteria (
  id             SMALLINT       PRIMARY KEY CHECK (id = 1),
  min_intensity  NUMERIC(6, 2)  NOT NULL CHECK (min_intensity > 0),
  changed_at     TIMESTAMPTZ    NOT NULL
);

-- 큰 판정 기준 이력 — 추가만 한다. 처음 값은 이전 값이 없다
CREATE TABLE storyline_big_criteria_changes (
  id                      BIGSERIAL      PRIMARY KEY,
  previous_min_intensity  NUMERIC(6, 2),
  min_intensity           NUMERIC(6, 2)  NOT NULL,
  actor                   TEXT           NOT NULL CHECK (actor IN ('SYSTEM', 'OPERATOR')),
  changed_at              TIMESTAMPTZ    NOT NULL
);
