-- 잇기 대기의 일시 실패 수 — 흐름 소속 판정이 평가기 일시 실패로 끝난 수. 판정 가능 시각의 간격만 정하고 상한이 없어 대기에서 빼지 않는다.
-- failures 는 판정이 예상하지 못한 예외로 실패한 수만 센다(상한에 이르면 대기에서 뺀다) — docs/specs/storyline.md §5 · storyline-R4.
--
-- 이미 있는 행의 failures 는 두 실패를 함께 센 값이라 가를 수 없어 그대로 둔다.
ALTER TABLE storyline_linking_queue
  ADD COLUMN transient_failures INTEGER NOT NULL DEFAULT 0 CHECK (transient_failures >= 0);
