-- 다이제스트 출력의 빠진 항목 기록 — docs/specs/digest.md §6 · digest-R6 · digest-R27.
--
-- first_missing_items 는 처음 읽은 출력에 칸이 없던 항목 수, substituted_items 는 다시 물은 뒤에도 없어 대체 헤드라인으로 남긴 항목 수다.
-- READY 로 끝낸 다이제스트만 갖고 둘은 함께 있다. 이 마이그레이션 전에 끝난 READY 행은 고쳐 쓰지 않아 둘 다 NULL(기록 전)이다.
ALTER TABLE digests ADD COLUMN first_missing_items INTEGER CHECK (first_missing_items >= 0);
ALTER TABLE digests ADD COLUMN substituted_items   INTEGER CHECK (substituted_items >= 0);
ALTER TABLE digests ADD CONSTRAINT digests_item_fills_ready
  CHECK (status = 'READY' OR (first_missing_items IS NULL AND substituted_items IS NULL));
ALTER TABLE digests ADD CONSTRAINT digests_item_fills_pair
  CHECK ((first_missing_items IS NULL) = (substituted_items IS NULL));
