-- 측정용 시계의 상태 — 시계 행 하나와 넘긴 기록 — docs/specs/platform.md §6.
--
-- 운영자가 측정 DB 에 시계 행(출발 시각)을 손으로 넣는다. 시계 행이 있는 DB 는 측정 profile 로만 뜨고, 측정 profile 은 시계 행이 있어야
-- 뜬다(platform 의 기동 검사). anchored_at 은 출발 시각에 대응하는 실제 시각이고 처음 뜰 때 앱이 채운다. 넘긴 기록은 운영자 API 가
-- 넘길 때마다 한 행을 더한다 — 시각은 모두 측정 시각이다.
CREATE TABLE measurement_clock (
  id           SMALLINT     PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  started_at   TIMESTAMPTZ  NOT NULL,
  anchored_at  TIMESTAMPTZ
);

CREATE TABLE measurement_clock_jumps (
  id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  clock_id  SMALLINT     NOT NULL REFERENCES measurement_clock (id),
  from_at   TIMESTAMPTZ  NOT NULL,
  to_at     TIMESTAMPTZ  NOT NULL,
  CHECK (to_at > from_at)
);
