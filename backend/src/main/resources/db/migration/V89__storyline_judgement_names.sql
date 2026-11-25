-- evaluation 모듈 — 판정 원본 표 둘의 이름을 개발 이름(스토리라인)으로 바꾼다.
--
-- 표 · 칸 · 제약 · 인덱스 · identity 시퀀스의 이름만 바꾸고 행은 그대로 둔다. 제약은 직접 이름 지은 것(distinct_flows)과
-- PostgreSQL 이 표 · 칸 이름으로 지은 것(기본 키 · NOT NULL · CHECK)을 모두 표의 제약 목록에서 찾아 바꾼다. 스토리라인 소속 판정
-- 원본의 물은 후보 jsonb 는 원소의 ID 키를 flowId 에서 candidateId 로 바꾼다 — 후보는 부르는 쪽(storyline)의 스토리라인(붙이기)이나
-- 이웃 사건(짝)이다. 원소 순서(요청 순서)와 그 밖의 키 · 값은 그대로다.

ALTER TABLE flow_relation_judgements RENAME TO storyline_relation_judgements;
ALTER TABLE storyline_relation_judgements RENAME COLUMN first_flow_id TO first_storyline_id;
ALTER TABLE storyline_relation_judgements RENAME COLUMN second_flow_id TO second_storyline_id;
ALTER TABLE flow_membership_judgements RENAME TO storyline_membership_judgements;

DO $$
DECLARE
  judgement RECORD;
BEGIN
  FOR judgement IN
    SELECT conrelid::regclass AS judgements, conname
      FROM pg_constraint
     WHERE conrelid IN ('storyline_relation_judgements'::regclass, 'storyline_membership_judgements'::regclass)
       AND conname LIKE 'flow\_%'
  LOOP
    EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', judgement.judgements, judgement.conname,
      replace(replace(replace(replace(judgement.conname,
        'flow_relation_judgements', 'storyline_relation_judgements'),
        'flow_membership_judgements', 'storyline_membership_judgements'),
        '_flow_id_', '_storyline_id_'),
        '_distinct_flows', '_distinct_storylines'));
  END LOOP;
END $$;

ALTER INDEX flow_relation_judgements_judged_idx RENAME TO storyline_relation_judgements_judged_idx;
ALTER SEQUENCE flow_relation_judgements_id_seq RENAME TO storyline_relation_judgements_id_seq;
ALTER INDEX flow_membership_judgements_article_idx RENAME TO storyline_membership_judgements_article_idx;
ALTER INDEX flow_membership_judgements_judged_idx RENAME TO storyline_membership_judgements_judged_idx;
ALTER SEQUENCE flow_membership_judgements_id_seq RENAME TO storyline_membership_judgements_id_seq;

UPDATE storyline_membership_judgements
SET candidates = coalesce((
    SELECT jsonb_agg((c - 'flowId') || jsonb_build_object('candidateId', c -> 'flowId') ORDER BY i)
    FROM jsonb_array_elements(candidates) WITH ORDINALITY AS t(c, i)), '[]'::jsonb)
WHERE jsonb_path_exists(candidates, '$[*].flowId');
