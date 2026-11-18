-- event 모듈 — 병합 확인의 미룸(docs/specs/event.md §6 · event-R13).
--
-- 병합 회차가 확인을 끝내지 못한 열린 사건은 표시 없이 뒤로 미룬다 — 이어서 미룬 횟수와 다시 볼 수 있는 시각을 사건 행에 둔다.
-- 미루지 않은 사건은 횟수 0 · 시각 없음이고, 둘은 함께 있거나 함께 없다.
ALTER TABLE events
  ADD COLUMN merge_deferrals  INTEGER      NOT NULL DEFAULT 0 CHECK (merge_deferrals >= 0),
  ADD COLUMN merge_retry_at   TIMESTAMPTZ,
  ADD CHECK ((merge_deferrals = 0) = (merge_retry_at IS NULL));
