-- 다이제스트의 요약문이 짧은 항목 기록 — docs/specs/digest.md §6 · digest-R47.
--
-- short_summary_items 는 항목의 요약문 길이(표본 기사 RSS 요약문의 코드 포인트 합)가 주요 뉴스 요약 하한보다 짧아 헤드라인이 된 항목
-- 가운데 하한 이상이었으면 주요 뉴스가 되었을 항목 수다. 생성의 입력에서 정해지는 값이라 시작할 때 후보 수와 함께 적고, 끝난 뒤에도 남는다.
-- 이 마이그레이션 전에 시작한 행은 고쳐 쓰지 않아 NULL(기록 전)이다. 값이 있으면 후보 수가 있고 그 수를 넘지 않는다.
ALTER TABLE digests ADD COLUMN short_summary_items INTEGER CHECK (short_summary_items >= 0);
ALTER TABLE digests ADD CONSTRAINT digests_short_summary_started
  CHECK (short_summary_items IS NULL OR (candidate_count IS NOT NULL AND short_summary_items <= candidate_count));
