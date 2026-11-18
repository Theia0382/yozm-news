-- 같은 사안의 조각을 한 스토리라인으로 합친다 — docs/specs/storyline.md §6 · storyline-R17.
--
-- 소속의 merged_from: 합치기로 남는 스토리라인에 옮겨 온 소속이면 그 사건을 직전에 가졌던 합쳐진 스토리라인의 ID 다. 합쳐진 행이
-- 지워져도 값은 남는다(FK 없음).
ALTER TABLE storyline_members ADD COLUMN merged_from BIGINT;

-- 합쳐진 행의 근거: 조각 합치기면 그 관계 판정 원본 ID(다른 모듈의 원본이라 FK 없음)와 그때 쓴 관계 기준 이력 ID 다. 합친 쌍은 물은
-- 기록이 남지 않으므로 기준을 여기에 남긴다. 정돈의 합치기(구성 사건이 같음 · 사건 하나만 남음)면 둘 다 없다.
ALTER TABLE storylines
  ADD COLUMN merge_judgement_id  BIGINT,
  ADD COLUMN merge_criteria_id   BIGINT REFERENCES storyline_relation_criteria,
  ADD CONSTRAINT storylines_merge_basis_check CHECK (
    (merge_judgement_id IS NULL) = (merge_criteria_id IS NULL)
    AND (merge_judgement_id IS NULL OR merged_into IS NOT NULL)
  );

-- 관계 기준의 합치기 확률 기준: 조각 답의 확률이 이 이상이면 합친다. 지금까지의 기준 행은 조각을 합치지 않던 기준이라 1.0 으로 둔다.
ALTER TABLE storyline_relation_criteria
  ADD COLUMN merge_probability DOUBLE PRECISION NOT NULL DEFAULT 1.0;
ALTER TABLE storyline_relation_criteria
  ALTER COLUMN merge_probability DROP DEFAULT,
  ADD CONSTRAINT storyline_relation_criteria_merge_probability_check
    CHECK (merge_probability >= probability AND merge_probability <= 1),
  DROP CONSTRAINT storyline_relation_criteria_probability_one_matter_min_even_key,
  ADD CONSTRAINT storyline_relation_criteria_values_key
    UNIQUE (probability, one_matter, min_events, candidate_score, window_seconds, merge_probability);
