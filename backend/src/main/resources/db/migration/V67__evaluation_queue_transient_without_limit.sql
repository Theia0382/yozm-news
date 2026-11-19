-- 평가 대기의 일시 실패는 시도 상한 없이 재시도 간격 뒤로 미루고 실패로 남기지 않는다 — docs/specs/tag.md §6 · tag-R20.
--
-- 시도 상한에 이르러 실패(RETRIES_EXHAUSTED)로 남았던 행을 지금 평가할 수 있는 대기로 되돌린다. 시도 수와 마지막 문구는 그대로 둔다 —
-- 다음 일시 실패의 간격이 이어진다. 그 뒤 실패 까닭은 기사 거부 하나다.
UPDATE evaluation_queue SET failure = NULL, eligible_at = now() WHERE failure = 'RETRIES_EXHAUSTED';

ALTER TABLE evaluation_queue DROP CONSTRAINT evaluation_queue_failure_check;
ALTER TABLE evaluation_queue ADD CONSTRAINT evaluation_queue_failure_check CHECK (failure IN ('ARTICLE_REJECTED'));
