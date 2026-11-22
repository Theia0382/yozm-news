-- digest 모듈 — 항목을 묶은 흐름(docs/specs/digest.md §6).
-- 인용 섹션(항목)을 묶은 포함 나무의 꼭대기 — 생성 때의 값. 혼자 한 항목 · 개요 · 이 칸보다 앞서 만든 섹션은 NULL 이다.
-- storyline_id 는 storyline 모듈의 ID 다 — 모듈 사이라 FK 를 두지 않는다. 합쳐지거나 지워진 스토리라인은 조회가 푼다.
ALTER TABLE digest_sections
  ADD COLUMN storyline_id BIGINT CHECK (storyline_id >= 1);
