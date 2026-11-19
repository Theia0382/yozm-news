-- 관계 대기의 판정 가능 시각 — 관계 판정이 일시 실패한 스토리라인을 이 시각까지 미룬다. 없으면 바로 판정할 수 있다 —
-- docs/specs/storyline.md §6 · storyline-R31.
ALTER TABLE storyline_relation_queue ADD COLUMN retry_at TIMESTAMPTZ;
