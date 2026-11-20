-- 인용(digest_items)의 선정 이유 칸을 지운다 — docs/specs/digest.md §6.
--
-- 선정 이유는 사건마다 섹션 사건 행(digest_section_events.selection_reason)에 있고, 반응 · 노출의 단위도 사건이다. 인용은 항목의
-- 표본 기사(화면 출처 목록)만 갖는다. 지우는 값은 사건의 이유에서 옮겨 적은 파생 값이라 옮기지 않는다.
ALTER TABLE digest_items DROP COLUMN selection_reason;
