-- event 모듈 — 사건 핵심 말의 말 인덱스(docs/specs/event.md §6 · event-R24).
--
-- 말 수 조회가 말로 핵심 말 행을 찾는다. 기본 키 (event_id, term) 는 말로 시작하지 않아 이 조회를 거르지 못한다.
CREATE INDEX event_key_terms_term_idx ON event_key_terms (term);
