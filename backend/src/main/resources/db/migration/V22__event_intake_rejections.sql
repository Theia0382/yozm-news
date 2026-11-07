-- event 모듈 — 사건 묶기 대기의 거부 수(docs/specs/event.md §6 · event-R6).
--
-- 임베더가 그 기사의 입력을 거부한 횟수다. 상한(IntakeSchedule.MAX_REJECTIONS)에 이르면 회차가 대기 행을 지운다.
ALTER TABLE event_intake_queue
  ADD COLUMN rejections INTEGER NOT NULL DEFAULT 0 CHECK (rejections >= 0);
