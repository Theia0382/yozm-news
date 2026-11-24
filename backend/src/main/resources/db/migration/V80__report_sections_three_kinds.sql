-- 변화 리포트의 갈래를 셋으로 줄인다(ADR-0303) — 섹션 종류에서 RISE_AND_FALL 을 뺀다.
-- 행은 지우지 않는다(ADR-0212) — 그 종류의 행이 있으면 제약을 다시 걸 때 실패해 드러난다.
ALTER TABLE report_sections
  DROP CONSTRAINT report_sections_kind_check,
  ADD CONSTRAINT report_sections_kind_check CHECK (kind IN ('OVERVIEW','STORYLINES','COVERAGE','INTERESTS'));
