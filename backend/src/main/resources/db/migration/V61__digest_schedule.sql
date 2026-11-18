-- digest 모듈 — 다이제스트 일정(docs/specs/digest.md §6).

-- 생성 작업의 등록 토큰과 마지막 등록 시각 — 작업 ID 가 토큰이고, 다시 등록하면 새 토큰이다
ALTER TABLE digests ADD COLUMN dispatch_token UUID;
ALTER TABLE digests ADD COLUMN dispatched_at TIMESTAMPTZ;
UPDATE digests SET dispatch_token = gen_random_uuid(), dispatched_at = created_at;
ALTER TABLE digests ALTER COLUMN dispatch_token SET NOT NULL;
ALTER TABLE digests ALTER COLUMN dispatched_at SET NOT NULL;

-- 같은 사용자 · 날짜의 일정 다이제스트는 하나다 — 일정 회차가 겹쳐도 한 번만 만든다. 수동 생성은 날짜마다 여럿일 수 있다
CREATE UNIQUE INDEX digests_scheduled_date_key ON digests (user_id, digest_date) WHERE trigger = 'SCHEDULED';

-- 끝나지 않은 다이제스트 — 멈춘 생성을 거두는 회차와 진행 중 확인이 찾는다
CREATE INDEX digests_unfinished_idx ON digests (status, user_id) WHERE status IN ('SCHEDULED', 'RUNNING');
