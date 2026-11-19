-- 인용(digest_items)의 코멘트 칸을 지운다 — 헤드라인의 코멘트는 항목마다의 헤드라인 섹션 본문(digest_sections.body)이다(digest-R7).
-- 인용은 기사 ID 와 그 사건의 선정 이유만 갖는다
ALTER TABLE digest_items DROP COLUMN note;
