-- 관계 판정의 문구 판이 바뀌어(같은 사안의 조각 답) 모든 쌍을 다시 묻게 한다 — docs/specs/storyline.md §6 · storyline-R30 · R31.
--
-- 물은 기록을 비우면 후보 쌍이 다시 물을 때가 된다. 합쳐지지 않은 스토리라인을 모두 관계 대기에 넣어 관계 회차가 차례로 다시
-- 묻게 한다 — 이미 대기에 있는 스토리라인은 알림 수를 올린다(앱의 대기 넣기와 같다).
-- 지금 관계는 지우지 않는다 — 다시 물은 쌍은 그 판정이 고쳐 쓰고, 다시 묻지 않는 쌍의 관계는 그대로 남는다.
DELETE FROM storyline_relation_asked;

INSERT INTO storyline_relation_queue (storyline_id, enqueued_at)
SELECT id, now() FROM storylines WHERE merged_into IS NULL
ON CONFLICT (storyline_id) DO UPDATE SET notifications = storyline_relation_queue.notifications + 1;
