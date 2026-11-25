-- report 모듈 — 변화 리포트 통계 스냅숏을 1판에서 2판으로 옮긴다(docs/specs/report.md §6).
--
-- 2판은 추적한 사용자 스토리라인 갈래(storylines)와 관심 변화 갈래(interests)의 사용자 스토리라인 줄을 키 userStorylines 로 쓴다.
-- 1판의 키 flows 를 userStorylines 로 바꾸고 판을 2 로 올린다 — 줄의 칸 · 값 · 순서는 그대로다. 스냅숏이 없는 행(시작 전)과
-- 1판이 아닌 행은 건드리지 않는다. 읽기는 판이 같을 때만 값을 내므로 옮긴 뒤에는 2판 하나만 읽는다.

UPDATE reports
SET stats_snapshot = jsonb_set(stats_snapshot, '{storylines}',
    ((stats_snapshot -> 'storylines') - 'flows')
    || jsonb_build_object('userStorylines', stats_snapshot -> 'storylines' -> 'flows'))
WHERE stats_snapshot ->> 'version' = '1'
  AND stats_snapshot -> 'storylines' -> 'flows' IS NOT NULL;

UPDATE reports
SET stats_snapshot = jsonb_set(stats_snapshot, '{interests}',
    ((stats_snapshot -> 'interests') - 'flows')
    || jsonb_build_object('userStorylines', stats_snapshot -> 'interests' -> 'flows'))
WHERE stats_snapshot ->> 'version' = '1'
  AND stats_snapshot -> 'interests' -> 'flows' IS NOT NULL;

UPDATE reports
SET stats_snapshot = jsonb_set(stats_snapshot, '{version}', '2'::jsonb)
WHERE stats_snapshot ->> 'version' = '1';
