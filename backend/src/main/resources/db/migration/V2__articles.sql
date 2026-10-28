-- article 모듈 — 중복 제거된 기사와, 그 기사를 어느 피드에서 언제 봤는지.
--
-- source_id · feed_id 는 source 모듈의 테이블을 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다.
-- 길이 상한이 없는 키(정규 URL · 제목 비교 키 · guid)는 SHA-256 해시로 인덱스와 비교에 쓴다 — btree 항목 크기 한계를
-- 넘는 값이 들어와도 INSERT 가 실패하지 않는다.

CREATE TABLE articles
(
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source_id              BIGINT      NOT NULL,
  canonical_url          TEXT        NOT NULL,
  url_hash               BYTEA       NOT NULL CHECK (octet_length(url_hash) = 32),
  title                  TEXT        NOT NULL,
  title_key_hash         BYTEA       NOT NULL CHECK (octet_length(title_key_hash) = 32),
  summary_text           TEXT,
  author                 TEXT,
  published_at           TIMESTAMPTZ NOT NULL,
  published_at_estimated BOOLEAN     NOT NULL,
  image_url              TEXT,
  first_seen_at          TIMESTAMPTZ NOT NULL,
  CONSTRAINT articles_url_hash_key UNIQUE (url_hash)
);
CREATE INDEX articles_title_match_idx ON articles (source_id, title_key_hash, published_at);

-- 기사 · 피드마다 한 행 — 그 피드에서 처음 본 항목의 값이 남는다.
CREATE TABLE article_feed_entries
(
  article_id             BIGINT      NOT NULL REFERENCES articles (id),
  feed_id                BIGINT      NOT NULL,
  item_url_hash          BYTEA       NOT NULL CHECK (octet_length(item_url_hash) = 32),
  guid                   TEXT,
  guid_hash              BYTEA       CHECK (octet_length(guid_hash) = 32),
  feed_categories        TEXT[]      NOT NULL,
  published_at_estimated BOOLEAN     NOT NULL,
  match_kind             TEXT        NOT NULL CHECK (match_kind IN ('NEW', 'URL', 'GUID', 'TITLE')),
  seen_at                TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (article_id, feed_id),
  CHECK ((guid IS NULL) = (guid_hash IS NULL))
);
CREATE INDEX article_feed_entries_item_url_idx ON article_feed_entries (item_url_hash);
CREATE INDEX article_feed_entries_guid_idx ON article_feed_entries (feed_id, guid_hash) WHERE guid_hash IS NOT NULL;
