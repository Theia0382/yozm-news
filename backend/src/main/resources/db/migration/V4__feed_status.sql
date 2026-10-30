-- 피드 상태(source-R30 · source-R31)와 그 판정 입력.
--
-- 수집 상태 테이블을 쓰는 쪽이 둘이 된다. 수집 작업은 판정 입력을 포함한 수집 상태를 쓰고 status · status_changed_at 은
-- 쓰지 않는다. 피드 건강 검사는 status · status_changed_at 만 쓴다. 판정 기준은 source 모듈의 FeedStatusRule 이 정본이라
-- 여기에 두지 않는다.
ALTER TABLE feed_poll_states
  -- 연속 실패 가운데 영구 실패의 수. 이미 연속 실패 중인 행도 0 에서 시작한다 — 분류는 source 모듈이 정본이다
  ADD COLUMN consecutive_permanent_failures INTEGER     NOT NULL DEFAULT 0,
  -- 새 항목의 발행 시각 가운데 가장 늦은 것. 비어 있으면 아직 없다
  ADD COLUMN latest_published_at            TIMESTAMPTZ,
  -- 피드 상태. 수집 작업이 행을 만들 때는 이 열을 모르므로 기본값이 첫 상태다
  ADD COLUMN status                         TEXT        NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'DEGRADED', 'STALE')),
  -- 건강 검사가 상태를 마지막으로 바꾼 시각. 비어 있으면 바꾼 적이 없다
  ADD COLUMN status_changed_at              TIMESTAMPTZ,
  ADD CHECK (consecutive_permanent_failures BETWEEN 0 AND consecutive_failures);
