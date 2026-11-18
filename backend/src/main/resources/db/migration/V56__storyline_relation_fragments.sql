-- 스토리라인 관계의 물은 기록에 같은 사안의 조각으로 판정된 쌍의 결말을 더한다 — docs/specs/storyline.md §6 · storyline-R30.
--
-- UNMERGED 는 조각으로 판정됐으나 확률이 합치기 확률 기준에 못 미쳐 합치지 않은 것이다. 합친 쌍은 한쪽이 사라져 물은 기록에 남지 않는다.
ALTER TABLE storyline_relation_asked DROP CONSTRAINT storyline_relation_asked_outcome_check;
ALTER TABLE storyline_relation_asked ADD CONSTRAINT storyline_relation_asked_outcome_check
  CHECK (outcome IN ('LINKED', 'NOT_RELATED', 'LOW_PROBABILITY', 'NOT_ONE_MATTER', 'CYCLE', 'SECOND_PARENT',
                     'UNMERGED', 'NOTHING_TO_ASK', 'REJECTED'));
