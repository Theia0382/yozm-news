-- evaluation 모듈 — 태그 키 계보(docs/specs/evaluation.md §6).
--
-- 버전 n 에서 기준 버전(n − 1)의 이전 키가 어느 새 키로 이어지는지(evaluation-R22 · R23). 버전과 함께 한 번 쓰고 바꾸지 않는다.
-- 이전 키는 기준 버전의 키라 FK 를 둘 수 없다 — 활성화가 확인한다. 태그 종류 · 계보 종류는 대문자 상수 이름이다.
CREATE TABLE tag_key_lineage (
  tag_set_id  BIGINT NOT NULL REFERENCES tag_sets (id),
  tag_kind    TEXT   NOT NULL CHECK (tag_kind IN ('TOPIC', 'REGION', 'FORMAT')),
  from_key    TEXT   NOT NULL,
  to_key      TEXT   NOT NULL,
  kind        TEXT   NOT NULL CHECK (kind IN ('RENAME', 'SPLIT', 'MERGE')),
  PRIMARY KEY (tag_set_id, tag_kind, from_key, to_key),
  -- 새 키는 같은 버전 · 같은 태그 종류의 태그 행. aggregate 자식 행의 저장 순서가 정해지지 않아 커밋 때 확인한다
  FOREIGN KEY (tag_set_id, tag_kind, to_key) REFERENCES tags (tag_set_id, kind, key) DEFERRABLE INITIALLY DEFERRED
);
