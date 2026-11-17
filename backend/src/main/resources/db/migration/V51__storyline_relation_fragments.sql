-- 스토리라인 관계의 물은 기록에 같은 사안의 조각으로 판정된 쌍의 결말을 더한다 — docs/specs/storyline.md §6 · storyline-R30.
--
-- UNGROUPED 는 조각으로 판정됐으나 묶지 않은 것, GROUPED 는 한 상위 아래 묶은 것, REFERRED 는 한쪽의 상위와의 쌍을 넘겨 물은 것이다.
ALTER TABLE storyline_relation_asked DROP CONSTRAINT storyline_relation_asked_outcome_check;
ALTER TABLE storyline_relation_asked ADD CONSTRAINT storyline_relation_asked_outcome_check
  CHECK (outcome IN ('LINKED', 'NOT_RELATED', 'LOW_PROBABILITY', 'NOT_ONE_MATTER', 'CYCLE', 'SECOND_PARENT',
                     'UNGROUPED', 'GROUPED', 'REFERRED', 'NOTHING_TO_ASK', 'REJECTED'));
