-- article 모듈 — 기사의 소스를 피드별 기록(엔트리)이 갖는다(article-R3 · article-R7).
--
-- 기사 하나가 여러 소스의 엔트리를 가질 수 있다 — 기사의 소스는 그 엔트리들의 소스다. 제목 판정은 그 소스의 엔트리가 있는 기사에서
-- 한다. source_id 는 source 모듈의 sources 를 가리키지만 FK 를 두지 않는다 — 모듈 사이는 ID 로만 참조한다.
ALTER TABLE article_feed_entries ADD COLUMN source_id BIGINT;

-- 이미 있는 엔트리는 그 피드의 소스로 채운다. 한 번의 스키마 이행이라 source 모듈의 feeds 를 읽는다 — 피드의 소스는 바뀌지 않는다
UPDATE article_feed_entries e SET source_id = f.source_id FROM feeds f WHERE f.id = e.feed_id;

ALTER TABLE article_feed_entries ALTER COLUMN source_id SET NOT NULL;
CREATE INDEX article_feed_entries_source_idx ON article_feed_entries (source_id, article_id);

DROP INDEX articles_title_match_idx;
CREATE INDEX articles_title_match_idx ON articles (title_key_hash, published_at);
ALTER TABLE articles DROP COLUMN source_id;
