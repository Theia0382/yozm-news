-- 다이제스트 섹션을 항목 단위로 — 섹션 하나가 항목 하나(주요 뉴스 · 헤드라인)이고 그 항목의 사건을 가리킨다 — docs/specs/digest.md §6.
--
-- 기사 단위로 만든 옛 섹션 · 인용 · 후보 행은 옮기지 않고 지운다(릴리스 전이고 그 데이터를 가진 인스턴스가 없다). 다이제스트 행은 남아 옛
-- 다이제스트는 내용 없는 READY 로 보인다. 인용(digest_items)은 섹션을 지우면 함께 지워진다. digest_items.note 는 이 마이그레이션에서 두고
-- 새 행은 늘 NULL 이다.
DELETE FROM digest_sections;
DELETE FROM digest_candidates;

ALTER TABLE digest_sections DROP CONSTRAINT digest_sections_kind_check;
ALTER TABLE digest_sections ADD CONSTRAINT digest_sections_kind_check
  CHECK (kind IN ('OVERVIEW','TOP_STORY','HEADLINE'));

-- 섹션 ↔ 사건 — 항목의 사건과 그 선정 이유. event_id 는 모듈 사이라 FK 가 없다
CREATE TABLE digest_section_events (
  digest_section_id BIGINT   NOT NULL REFERENCES digest_sections (id) ON DELETE CASCADE,
  position          INTEGER  NOT NULL CHECK (position >= 0),
  event_id          BIGINT   NOT NULL CHECK (event_id >= 1),
  selection_reason  TEXT     NOT NULL CHECK (selection_reason IN ('SUBSCRIBED','INTEREST','MAJOR_STORYLINE')),
  PRIMARY KEY (digest_section_id, position)
);
CREATE INDEX digest_section_events_event_idx ON digest_section_events (event_id);
