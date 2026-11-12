-- event 모듈 — 큰 사건 기준과 그 이력을 지운다. 큰 판정과 그 기준은 storyline 이 갖는다(docs/specs/storyline.md §6 · storyline-R20).
--
-- 값 · 이력은 옮기지 않는다 — 사건 단위로 맞춘 값은 스토리라인 판정에서 뜻이 다르다. V18 의 기간 인덱스(event_articles_published_idx)는 남는다.

DROP TABLE event_big_criteria_changes;
DROP TABLE event_big_criteria;
